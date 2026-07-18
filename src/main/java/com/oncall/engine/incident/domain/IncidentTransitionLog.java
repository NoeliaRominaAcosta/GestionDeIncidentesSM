package com.oncall.engine.incident.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "incident_transition_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTransitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(nullable = false)
    private String event;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy; // e.g., "SYSTEM" or username

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
