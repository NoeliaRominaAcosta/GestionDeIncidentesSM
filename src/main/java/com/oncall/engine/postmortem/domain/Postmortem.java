package com.oncall.engine.postmortem.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "postmortem")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Postmortem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private Long incidentId;

    @Column(name = "root_cause", nullable = false, length = 2000)
    private String rootCause;

    @Column(name = "action_items", nullable = false, length = 2000)
    private String actionItems;

    @Column(nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
