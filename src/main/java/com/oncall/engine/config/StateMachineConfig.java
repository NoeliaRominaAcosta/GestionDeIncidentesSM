package com.oncall.engine.config;

import com.oncall.engine.incident.domain.State;
import com.oncall.engine.statemachine.event.IncidentEvent;
import com.oncall.engine.statemachine.guard.AssigneeActiveGuard;
import com.oncall.engine.statemachine.guard.PostmortemMissingGuard;
import com.oncall.engine.statemachine.guard.PostmortemRequiredGuard;
import com.oncall.engine.statemachine.action.TransitionLoggingAction;
import com.oncall.engine.statemachine.IncidentStateMachinePersister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;
import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<State, IncidentEvent> {

    @Autowired
    private AssigneeActiveGuard assigneeActiveGuard;

    @Autowired
    private PostmortemRequiredGuard postmortemRequiredGuard;

    @Autowired
    private PostmortemMissingGuard postmortemMissingGuard;

    @Autowired
    private TransitionLoggingAction transitionLoggingAction;

    @Autowired
    private IncidentStateMachinePersister incidentStateMachinePersister;

    @Bean
    public StateMachinePersister<State, IncidentEvent, Long> persister() {
        return new DefaultStateMachinePersister<>(incidentStateMachinePersister);
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<State, IncidentEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false);
    }

    @Override
    public void configure(StateMachineStateConfigurer<State, IncidentEvent> states) throws Exception {
        states.withStates()
                .initial(State.DETECTADO)
                .states(EnumSet.allOf(State.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<State, IncidentEvent> transitions) throws Exception {
        transitions
                // 1. DETECTADO -> TRIAGE (Transición automática inicial)
                .withExternal()
                    .source(State.DETECTADO)
                    .target(State.TRIAGE)
                    .action(transitionLoggingAction)
                    .and()

                // 2. TRIAGE -> ASIGNADO (Se asigna un usuario activo)
                .withExternal()
                    .source(State.TRIAGE)
                    .target(State.ASIGNADO)
                    .event(IncidentEvent.ASIGNAR)
                    .guard(assigneeActiveGuard)
                    .action(transitionLoggingAction)
                    .and()

                // 3. TRIAGE -> TRIAGE (Bucle interno para escalamiento/reintento)
                .withExternal()
                    .source(State.TRIAGE)
                    .target(State.TRIAGE)
                    .event(IncidentEvent.TIMEOUT_TRIAGE)
                    .action(transitionLoggingAction)
                    .and()

                // 4. ASIGNADO -> EN_MITIGACION (Responsable confirma / Acknowledge)
                .withExternal()
                    .source(State.ASIGNADO)
                    .target(State.EN_MITIGACION)
                    .event(IncidentEvent.CONFIRMAR)
                    .action(transitionLoggingAction)
                    .and()

                // 5. ASIGNADO -> TRIAGE (Responsable no responde a tiempo, expira timeout)
                .withExternal()
                    .source(State.ASIGNADO)
                    .target(State.TRIAGE)
                    .event(IncidentEvent.TIMEOUT_ACK)
                    .action(transitionLoggingAction)
                    .and()

                // 6. EN_MITIGACION -> MITIGADO (Impacto cesa)
                .withExternal()
                    .source(State.EN_MITIGACION)
                    .target(State.MITIGADO)
                    .event(IncidentEvent.MITIGAR)
                    .action(transitionLoggingAction)
                    .and()

                // 7. MITIGADO -> CERRADO (Intento de cierre de SEV3/SEV4, o SEV1/SEV2 con Postmortem ya hecho)
                .withExternal()
                    .source(State.MITIGADO)
                    .target(State.CERRADO)
                    .event(IncidentEvent.CERRAR)
                    .guard(postmortemRequiredGuard)
                    .action(transitionLoggingAction)
                    .and()

                // 8. MITIGADO -> POST_MORTEM_PENDIENTE (Intento de cierre de SEV1/SEV2 sin Postmortem registrado)
                .withExternal()
                    .source(State.MITIGADO)
                    .target(State.POST_MORTEM_PENDIENTE)
                    .event(IncidentEvent.CERRAR)
                    .guard(postmortemMissingGuard)
                    .action(transitionLoggingAction)
                    .and()

                // 9. POST_MORTEM_PENDIENTE -> CERRADO (Se sube causa raíz y se completa postmortem)
                .withExternal()
                    .source(State.POST_MORTEM_PENDIENTE)
                    .target(State.CERRADO)
                    .event(IncidentEvent.COMPLETAR_POSTMORTEM)
                    .action(transitionLoggingAction)
                    .and()

                // 10. CERRADO -> EN_MITIGACION (Reapertura directa por regresión)
                .withExternal()
                    .source(State.CERRADO)
                    .target(State.EN_MITIGACION)
                    .event(IncidentEvent.REABRIR)
                    .action(transitionLoggingAction);
    }
}
