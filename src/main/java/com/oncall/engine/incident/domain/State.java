package com.oncall.engine.incident.domain;

public enum State {
    DETECTADO,
    TRIAGE,
    ASIGNADO,
    EN_MITIGACION,
    MITIGADO,
    POST_MORTEM_PENDIENTE,
    CERRADO
}
