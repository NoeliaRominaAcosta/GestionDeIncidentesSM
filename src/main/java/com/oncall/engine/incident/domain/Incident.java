package com.oncall.engine.incident.domain;

import com.oncall.engine.schedule.domain.OnCallSchedule;
import com.oncall.engine.user.domain.AppUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "incident")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.DETECTADO;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    @Column(name = "mitigated_at")
    private LocalDateTime mitigatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @Column(name = "escalation_exhausted", nullable = false)
    @Builder.Default
    private boolean escalationExhausted = false;

    @Column(name = "escalation_count", nullable = false)
    @Builder.Default
    private int escalationCount = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignee_id")
    private AppUser currentAssignee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_id")
    private OnCallSchedule onCallSchedule;
}
