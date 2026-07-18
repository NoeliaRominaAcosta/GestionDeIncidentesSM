package com.oncall.engine.schedule.repository;

import com.oncall.engine.schedule.domain.OnCallSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnCallScheduleRepository extends JpaRepository<OnCallSchedule, Long> {
}
