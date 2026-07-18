package com.oncall.engine.schedule.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "on_call_member")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnCallMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private OnCallSchedule schedule;

    @Column(nullable = false)
    private String username; // Se corresponde con el username de AppUser

    @Column(name = "member_order", nullable = false)
    private int memberOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;
}
