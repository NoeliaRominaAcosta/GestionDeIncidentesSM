package com.oncall.engine.postmortem.service;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.incident.service.IncidentTransitionService;
import com.oncall.engine.postmortem.domain.Postmortem;
import com.oncall.engine.postmortem.repository.PostmortemRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PostmortemService {

    @Autowired
    private PostmortemRepository postmortemRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentTransitionService transitionService;

    /**
     * Registra un post-mortem para un incidente en estado POST_MORTEM_PENDIENTE
     * y gatilla la transición automática a CERRADO.
     */
    @Transactional
    public Postmortem createPostmortem(Long incidentId, String rootCause, String actionItems, String author) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incidente no encontrado con ID: " + incidentId));

        if (incident.getState() != State.POST_MORTEM_PENDIENTE) {
            throw new IllegalStateException("Solo se puede completar el post-mortem en estado POST_MORTEM_PENDIENTE. Estado actual: " + incident.getState());
        }

        Postmortem postmortem = Postmortem.builder()
                .incidentId(incidentId)
                .rootCause(rootCause)
                .actionItems(actionItems)
                .author(author)
                .createdAt(LocalDateTime.now())
                .build();

        postmortem = postmortemRepository.save(postmortem);

        // 1. Enviar el evento COMPLETAR_POSTMORTEM a la máquina de estados
        transitionService.sendEvent(incidentId, IncidentEvent.COMPLETAR_POSTMORTEM, 
                Map.of("triggeredBy", author));

        // 2. Actualizar fecha de cierre en la entidad Incident
        incident = incidentRepository.findById(incidentId).orElseThrow();
        incident.setClosedAt(LocalDateTime.now());
        incident.setTimeoutAt(null);
        incidentRepository.save(incident);

        return postmortem;
    }
}
