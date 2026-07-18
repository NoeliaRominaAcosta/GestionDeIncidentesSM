package com.oncall.engine.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    private String username;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String role; // e.g., "USER", "ADMIN"

    @Column(name = "missed_acks_count", nullable = false)
    private int missedAcksCount = 0;
}
