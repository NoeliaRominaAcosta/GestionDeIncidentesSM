package com.oncall.engine.incident.service;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.schedule.service.EscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutScheduler.class);

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EscalationService escalationService;

    /**
     * Tarea programada que se ejecuta cada 5 segundos para verificar si algún incidente
     * activo ha superado su límite de tiempo asignado (timeout de triage o de confirmación/ACK).
     * Este mecanismo actúa como scheduler de respaldo resiliente ante reinicios.
     */
    @Scheduled(fixedRate = 5000)
    public void checkIncidentTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<Incident> expiredIncidents = incidentRepository.findAllByTimeoutAtBefore(now);

        if (!expiredIncidents.isEmpty()) {
            log.info("Se encontraron {} incidentes con temporizador expirado a las {}", expiredIncidents.size(), now);
        }

        for (Incident incident : expiredIncidents) {
            try {
                escalationService.handleTimeout(incident);
            } catch (Exception e) {
                log.error("Error al procesar la expiración del incidente ID {}", incident.getId(), e);
            }
        }
    }
}
