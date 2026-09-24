package com.railwindow.sim.repo;

import java.util.List;
import java.util.Optional;

import com.railwindow.sim.domain.PlanStatus;
import com.railwindow.sim.domain.WorkPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    Optional<WorkPlan> findByPlanNo(String planNo);

    /**
     * 时间上可能重叠的在期计划（SCHEDULED / ACTIVE）。
     * 半开区间 [start, end)，首尾相接不算重叠；真正是否冲突交由互斥矩阵逐对判定。
     */
    @org.springframework.data.jpa.repository.Query("""
            select distinct p from WorkPlan p
            where p.status in (:statuses)
              and p.windowStart < :end
              and p.windowEnd > :start
            """)
    List<WorkPlan> findOverlapping(@org.springframework.data.repository.query.Param("start") java.time.Instant start,
                                   @org.springframework.data.repository.query.Param("end") java.time.Instant end,
                                   @org.springframework.data.repository.query.Param("statuses") java.util.Collection<PlanStatus> statuses);
}
