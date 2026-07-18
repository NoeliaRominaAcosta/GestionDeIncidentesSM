package com.oncall.engine.statemachine;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

@Component
public class IncidentStateMachinePersister implements StateMachinePersist<State, IncidentEvent, Long> {

    @Autowired
    private IncidentRepository incidentRepository;

    @Override
    public void write(StateMachineContext<State, IncidentEvent> context, Long incidentId) throws Exception {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
        
        // Sincronizar el estado de la máquina con la entidad de base de datos
        incident.setState(context.getState());
        incidentRepository.save(incident);
    }

    @Override
    public StateMachineContext<State, IncidentEvent> read(Long incidentId) throws Exception {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
        
        // Reconstruir el contexto de la máquina a partir del estado guardado en base de datos
        return new DefaultStateMachineContext<>(
                incident.getState(),
                null,
                null,
                null
        );
    }
}
