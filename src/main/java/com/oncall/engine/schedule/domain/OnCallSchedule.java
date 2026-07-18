package com.oncall.engine.schedule.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "on_call_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnCallSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "current_index", nullable = false)
    private int currentIndex = 0;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("memberOrder ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OnCallMember> members = new ArrayList<>();
}
