package com.oncall.engine.postmortem.repository;

import com.oncall.engine.postmortem.domain.Postmortem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PostmortemRepository extends JpaRepository<Postmortem, Long> {
    Optional<Postmortem> findByIncidentId(Long incidentId);
}
