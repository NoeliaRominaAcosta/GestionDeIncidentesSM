package com.oncall.engine.schedule.service;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.Severity;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.incident.service.IncidentTransitionService;
import com.oncall.engine.schedule.domain.MemberStatus;
import com.oncall.engine.schedule.domain.OnCallMember;
import com.oncall.engine.schedule.domain.OnCallSchedule;
import com.oncall.engine.schedule.repository.OnCallScheduleRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import com.oncall.engine.user.domain.AppUser;
import com.oncall.engine.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OnCallScheduleRepository scheduleRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private IncidentTransitionService transitionService;

    /**
     * Inicia el proceso de Triage para un incidente nuevo.
     */
    @Transactional
    public void startTriage(Incident incident) {
        log.info("Iniciando Triage para el incidente {}", incident.getId());
        
        // 1. Establecer el timeout inicial de Triage basado en la severidad del incidente
        int triageMinutes = getTriageTimeoutMinutes(incident.getSeverity());
        incident.setTimeoutAt(LocalDateTime.now().plusMinutes(triageMinutes));
        incidentRepository.save(incident);

        // 2. Intentar asignar de inmediato al primer miembro disponible del cronograma de guardia
        assignNextOnCall(incident);
    }

    /**
     * Maneja la expiración de un temporizador de incidente (Triage o Ack).
     */
    @Transactional
    public void handleTimeout(Incident incident) {
        log.info("Manejando timeout por inactividad para incidente {} en estado {}", incident.getId(), incident.getState());

        if (incident.getState() == State.ASIGNADO) {
            // El responsable actual no confirmó (ACK) a tiempo
            AppUser previousAssignee = incident.getCurrentAssignee();
            if (previousAssignee != null) {
                log.warn("Usuario {} no confirmó (ACK) a tiempo para el incidente {}", 
                        previousAssignee.getUsername(), incident.getId());
                
                // Incrementar contador de fallas (missed acks)
                previousAssignee.setMissedAcksCount(previousAssignee.getMissedAcksCount() + 1);
                userRepository.save(previousAssignee);
            }

            // Incrementar métricas de escalamiento del incidente
            incident.setEscalationCount(incident.getEscalationCount() + 1);
            incident.setCurrentAssignee(null);
            incidentRepository.save(incident);

            // Disparar evento TIMEOUT_ACK en la máquina de estados (vuelve a TRIAGE)
            transitionService.sendEvent(incident.getId(), IncidentEvent.TIMEOUT_ACK, 
                    Map.of("triggeredBy", "SYSTEM"));

            // Avanzar el índice de la guardia al siguiente
            advanceScheduleIndex(incident.getOnCallSchedule());

            // Asignar al siguiente On-Call disponible en la guardia
            assignNextOnCall(incident);

        } else if (incident.getState() == State.TRIAGE) {
            log.warn("Incidente {} expiró en TRIAGE sin poder asignarse", incident.getId());

            // Avanzar el índice por timeout en triage
            advanceScheduleIndex(incident.getOnCallSchedule());

            // Disparar evento TIMEOUT_TRIAGE
            transitionService.sendEvent(incident.getId(), IncidentEvent.TIMEOUT_TRIAGE, 
                    Map.of("triggeredBy", "SYSTEM"));

            // Reintentar asignación
            assignNextOnCall(incident);
        }
    }

    /**
     * Intenta asignar el incidente al miembro activo actual del cronograma de guardia.
     */
    @Transactional
    public void assignNextOnCall(Incident incident) {
        OnCallSchedule schedule = incident.getOnCallSchedule();
        if (schedule == null) {
            log.error("El incidente {} no tiene un cronograma de guardia asignado.", incident.getId());
            return;
        }

        List<OnCallMember> members = schedule.getMembers();
        if (members == null || members.isEmpty()) {
            log.warn("La guardia {} no tiene miembros definidos.", schedule.getName());
            incident.setEscalationExhausted(true);
            incident.setTimeoutAt(null);
            incidentRepository.save(incident);
            return;
        }

        int size = members.size();
        int startIndex = schedule.getCurrentIndex();
        OnCallMember assignedMember = null;

        // Recorrer la lista circularmente empezando por el currentIndex para encontrar el primer miembro ACTIVE
        for (int i = 0; i < size; i++) {
            int checkIndex = (startIndex + i) % size;
            OnCallMember member = members.get(checkIndex);
            if (member.getStatus() == MemberStatus.ACTIVE) {
                assignedMember = member;
                // Guardar la posición actual en el cronograma
                schedule.setCurrentIndex(checkIndex);
                scheduleRepository.save(schedule);
                break;
            }
        }

        if (assignedMember != null) {
            // Encontró un responsable activo
            AppUser assignee = userRepository.findById(assignedMember.getUsername())
                    .orElseThrow(() -> new IllegalStateException("Usuario no encontrado en la base de datos: " + assignedMember.getUsername()));

            incident.setCurrentAssignee(assignee);
            incident.setAssignedAt(LocalDateTime.now());
            incident.setEscalationExhausted(false);

            // Calcular el nuevo timeout de confirmación (ACK) basado en la severidad
            int ackMinutes = getAckTimeoutMinutes(incident.getSeverity());
            incident.setTimeoutAt(LocalDateTime.now().plusMinutes(ackMinutes));
            incidentRepository.save(incident);

            log.info("Asignando incidente {} a {} (orden {}). Próximo timeout de ACK a las {}", 
                    incident.getId(), assignee.getUsername(), assignedMember.getMemberOrder(), incident.getTimeoutAt());

            // Enviar evento ASIGNAR a la StateMachine
            transitionService.sendEvent(
                    incident.getId(), 
                    IncidentEvent.ASIGNAR, 
                    Map.of(
                        "assignee", assignee.getUsername(),
                        "scheduleId", schedule.getId(),
                        "triggeredBy", "SYSTEM"
                    )
            );
        } else {
            // No hay miembros activos en toda la cadena de guardia (Guardia Agotada)
            log.warn("Escalamiento agotado para el incidente {}: No hay miembros activos en la guardia {}", 
                    incident.getId(), schedule.getName());
            incident.setEscalationExhausted(true);
            incident.setTimeoutAt(null);
            incident.setCurrentAssignee(null);
            incidentRepository.save(incident);
        }
    }

    private void advanceScheduleIndex(OnCallSchedule schedule) {
        if (schedule != null && schedule.getMembers() != null && !schedule.getMembers().isEmpty()) {
            int nextIndex = (schedule.getCurrentIndex() + 1) % schedule.getMembers().size();
            schedule.setCurrentIndex(nextIndex);
            scheduleRepository.save(schedule);
            log.debug("Avanzado currentIndex del schedule {} a {}", schedule.getId(), nextIndex);
        }
    }

    private int getAckTimeoutMinutes(Severity severity) {
        return switch (severity) {
            case SEV1 -> 1;
            case SEV2 -> 2;
            case SEV3 -> 5;
            case SEV4 -> 10;
        };
    }

    private int getTriageTimeoutMinutes(Severity severity) {
        return switch (severity) {
            case SEV1 -> 2;
            case SEV2 -> 5;
            case SEV3 -> 15;
            case SEV4 -> 30;
        };
    }
}
