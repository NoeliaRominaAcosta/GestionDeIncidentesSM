package com.oncall.engine.incident.repository;

import com.oncall.engine.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByTimeoutAtBefore(LocalDateTime now);
}
