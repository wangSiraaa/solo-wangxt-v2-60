package com.railwindow.sim.repo;

import java.util.Optional;

import com.railwindow.sim.domain.MutexRule;
import com.railwindow.sim.domain.MutexRuleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MutexRuleRepository extends JpaRepository<MutexRule, MutexRuleId> {

    /**
     * 按规范化顺序取两类作业的矩阵条目。
     */
    @Query("""
            select r from MutexRule r
            where r.workTypeA = :a and r.workTypeB = :b
            """)
    Optional<MutexRule> findPair(@Param("a") String normalizedA, @Param("b") String normalizedB);
}
