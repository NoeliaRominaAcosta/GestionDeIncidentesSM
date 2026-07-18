package com.oncall.engine.statemachine.guard;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.Severity;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.postmortem.repository.PostmortemRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.support.StateContext;
import org.springframework.stereotype.Component;

@Component
public class PostmortemRequiredGuard implements Guard<State, IncidentEvent> {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PostmortemRepository postmortemRepository;

    @Override
    public boolean evaluate(StateContext<State, IncidentEvent> context) {
        Long incidentId = context.getMessageHeaders().get("incidentId", Long.class);
        if (incidentId == null) {
            return false;
        }

        return incidentRepository.findById(incidentId).map(incident -> {
            Severity severity = incident.getSeverity();
            // SEV1 y SEV2 requieren obligatoriamente que exista el reporte de post-mortem
            if (severity == Severity.SEV1 || severity == Severity.SEV2) {
                return postmortemRepository.findByIncidentId(incidentId).isPresent();
            }
            return true; // SEV3 y SEV4 no tienen restricción
        }).orElse(false);
    }
}
