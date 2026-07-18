package com.oncall.engine.statemachine.event;

public enum IncidentEvent {
    CREAR_INCIDENTE,
    ASIGNAR,
    CONFIRMAR,
    MITIGAR,
    COMPLETAR_POSTMORTEM,
    CERRAR,
    TIMEOUT_TRIAGE,
    TIMEOUT_ACK,
    REABRIR
}
