package com.oncall.engine.schedule.repository;

import com.oncall.engine.schedule.domain.OnCallMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OnCallMemberRepository extends JpaRepository<OnCallMember, Long> {
    List<OnCallMember> findByScheduleIdOrderByMemberOrderAsc(Long scheduleId);
}
