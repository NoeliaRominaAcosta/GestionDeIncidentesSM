package com.oncall.engine.incident.service;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.Severity;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.schedule.domain.OnCallSchedule;
import com.oncall.engine.schedule.repository.OnCallScheduleRepository;
import com.oncall.engine.schedule.service.EscalationService;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class IncidentService {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OnCallScheduleRepository scheduleRepository;

    @Autowired
    private IncidentTransitionService transitionService;

    @Autowired
    private EscalationService escalationService;

    /**
     * Crea un incidente, lo transiciona a TRIAGE e inicia la asignación On-Call.
     */
    @Transactional
    public Incident createIncident(String title, String description, Severity severity, Long scheduleId, String createdBy) {
        OnCallSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Cronograma de guardia no encontrado con ID: " + scheduleId));

        Incident incident = Incident.builder()
                .title(title)
                .description(description)
                .severity(severity)
                .state(State.DETECTADO)
                .onCallSchedule(schedule)
                .build();

        incident = incidentRepository.save(incident);

        // 1. Transición automática inicial de DETECTADO a TRIAGE
        transitionService.sendEvent(incident.getId(), IncidentEvent.CREAR_INCIDENTE, 
                Map.of("triggeredBy", createdBy));

        // 2. Recargar entidad e iniciar el escalamiento/triage
        incident = incidentRepository.findById(incident.getId()).orElseThrow();
        escalationService.startTriage(incident);

        return incidentRepository.findById(incident.getId()).orElseThrow();
    }

    /**
     * Confirma la recepción del incidente (Acknowledge).
     */
    @Transactional
    public void acknowledge(Long incidentId, String username) {
        transitionService.sendEvent(incidentId, IncidentEvent.CONFIRMAR, 
                Map.of("triggeredBy", username));

        Incident incident = incidentRepository.findById(incidentId).orElseThrow();
        incident.setAckedAt(LocalDateTime.now());
        incident.setTimeoutAt(null); // Desactivar temporizador
        incidentRepository.save(incident);
    }

    /**
     * Marca el incidente como mitigado.
     */
    @Transactional
    public void mitigate(Long incidentId, String username) {
        transitionService.sendEvent(incidentId, IncidentEvent.MITIGAR, 
                Map.of("triggeredBy", username));

        Incident incident = incidentRepository.findById(incidentId).orElseThrow();
        incident.setMitigatedAt(LocalDateTime.now());
        incident.setTimeoutAt(null); // Desactivar temporizador
        incidentRepository.save(incident);
    }

    /**
     * Intenta cerrar el incidente.
     */
    @Transactional
    public void close(Long incidentId, String username) {
        transitionService.sendEvent(incidentId, IncidentEvent.CERRAR, 
                Map.of("triggeredBy", username));

        Incident incident = incidentRepository.findById(incidentId).orElseThrow();
        // Solo actualizar timestamp si la StateMachine movió el estado a CERRADO
        if (incident.getState() == State.CERRADO) {
            incident.setClosedAt(LocalDateTime.now());
            incident.setTimeoutAt(null);
            incidentRepository.save(incident);
        }
    }

    /**
     * Reabre un incidente cerrado, devolviéndolo a EN_MITIGACION.
     */
    @Transactional
    public void reopen(Long incidentId, String username) {
        transitionService.sendEvent(incidentId, IncidentEvent.REABRIR, 
                Map.of("triggeredBy", username));

        Incident incident = incidentRepository.findById(incidentId).orElseThrow();
        // Resetear marcas de mitigación y cierre anteriores
        incident.setMitigatedAt(null);
        incident.setClosedAt(null);
        incident.setTimeoutAt(null);
        incidentRepository.save(incident);
    }
}
