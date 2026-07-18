package com.oncall.engine.statemachine.guard;

import com.oncall.engine.incident.domain.State;
import com.oncall.engine.schedule.domain.MemberStatus;
import com.oncall.engine.schedule.domain.OnCallMember;
import com.oncall.engine.schedule.repository.OnCallMemberRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.support.StateContext;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AssigneeActiveGuard implements Guard<State, IncidentEvent> {

    @Autowired
    private OnCallMemberRepository memberRepository;

    @Override
    public boolean evaluate(StateContext<State, IncidentEvent> context) {
        String assignee = context.getMessageHeaders().get("assignee", String.class);
        Long scheduleId = context.getMessageHeaders().get("scheduleId", Long.class);

        if (assignee == null) {
            return false;
        }

        // Si se provee el scheduleId, validamos que sea miembro activo de esa guardia específica
        if (scheduleId != null) {
            List<OnCallMember> members = memberRepository.findByScheduleIdOrderByMemberOrderAsc(scheduleId);
            return members.stream()
                    .anyMatch(m -> m.getUsername().equals(assignee) && m.getStatus() == MemberStatus.ACTIVE);
        }

        return true;
    }
}
