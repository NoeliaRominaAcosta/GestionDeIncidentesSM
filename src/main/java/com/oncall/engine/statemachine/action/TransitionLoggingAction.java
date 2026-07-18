package com.oncall.engine.statemachine.action;

import com.oncall.engine.incident.domain.IncidentTransitionLog;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentTransitionLogRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.support.StateContext;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class TransitionLoggingAction implements Action<State, IncidentEvent> {

    @Autowired
    private IncidentTransitionLogRepository logRepository;

    @Override
    public void execute(StateContext<State, IncidentEvent> context) {
        Long incidentId = context.getMessageHeaders().get("incidentId", Long.class);
        if (incidentId == null) {
            return;
        }

        String triggeredBy = context.getMessageHeaders().get("triggeredBy", String.class);
        if (triggeredBy == null) {
            triggeredBy = "SYSTEM";
        }

        State source = context.getSource() != null ? context.getSource().getId() : null;
        State target = context.getTarget() != null ? context.getTarget().getId() : null;
        IncidentEvent event = context.getEvent();

        IncidentTransitionLog log = IncidentTransitionLog.builder()
                .incidentId(incidentId)
                .fromState(source != null ? source.name() : null)
                .toState(target != null ? target.name() : "UNKNOWN")
                .event(event != null ? event.name() : "AUTO")
                .triggeredBy(triggeredBy)
                .timestamp(LocalDateTime.now())
                .build();

        logRepository.save(log);
    }
}
