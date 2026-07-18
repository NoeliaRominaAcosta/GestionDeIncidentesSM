package com.oncall.engine.incident.repository;

import com.oncall.engine.incident.domain.IncidentTransitionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncidentTransitionLogRepository extends JpaRepository<IncidentTransitionLog, Long> {
    List<IncidentTransitionLog> findByIncidentIdOrderByTimestampAsc(Long incidentId);
}
