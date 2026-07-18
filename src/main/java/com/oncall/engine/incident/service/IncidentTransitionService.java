package com.oncall.engine.incident.service;

import com.oncall.engine.incident.domain.State;
import com.oncall.engine.statemachine.event.IncidentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class IncidentTransitionService {

    private static final Logger log = LoggerFactory.getLogger(IncidentTransitionService.class);

    @Autowired
    private StateMachineFactory<State, IncidentEvent> stateMachineFactory;

    @Autowired
    private StateMachinePersister<State, IncidentEvent, Long> persister;

    /**
     * Envía un evento a la máquina de estados asociada a un incidente y persiste el nuevo estado.
     *
     * @param incidentId ID del incidente.
     * @param event Evento a disparar.
     * @param headers Metadatos adicionales para la transición (ej., asignado, creador, etc.).
     * @return Instancia de la StateMachine en su estado final.
     */
    @Transactional
    public StateMachine<State, IncidentEvent> sendEvent(Long incidentId, IncidentEvent event, Map<String, Object> headers) {
        try {
            // 1. Obtener una instancia limpia de la máquina desde la factoría
            StateMachine<State, IncidentEvent> sm = stateMachineFactory.getStateMachine(incidentId.toString());

            // 2. Detener la máquina para poder restaurar el estado anterior
            sm.stopReactively().block();

            // 3. Restaurar el estado persistido en BD en la máquina
            persister.restore(sm, incidentId);

            // 4. Iniciar la máquina de estados reactiva
            sm.startReactively().block();

            // 5. Construir el mensaje con los headers requeridos
            MessageBuilder<IncidentEvent> messageBuilder = MessageBuilder.withPayload(event)
                    .setHeader("incidentId", incidentId);

            if (headers != null) {
                headers.forEach(messageBuilder::setHeader);
            }

            Message<IncidentEvent> message = messageBuilder.build();

            // 6. Enviar el evento de forma reactiva y esperar el resultado
            boolean accepted = sm.sendEventReactively(message)
                    .map(result -> result.getResultType() == org.springframework.statemachine.StateMachineEventResult.ResultType.ACCEPTED)
                    .block();

            if (!accepted) {
                log.warn("Transición denegada para el incidente {}: Evento {} no es válido en el estado {}", 
                        incidentId, event, sm.getState().getId());
                throw new IllegalStateException("El evento " + event + " no es válido desde el estado actual " + sm.getState().getId());
            }

            // 7. Persistir el nuevo estado en la base de datos
            persister.persist(sm, incidentId);

            return sm;
        } catch (Exception e) {
            log.error("Error al procesar la transición del incidente {} con evento {}", incidentId, event, e);
            throw new RuntimeException("Error al procesar la transición del incidente: " + e.getMessage(), e);
        }
    }
}
