package com.railwindow.sim.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.railwindow.sim.domain.MutexRule;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.repo.MutexRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于<b>互斥作业矩阵</b>的冲突判断。
 *
 * <p>判定步骤：① 两个窗口时间重叠（半开区间）；② 存在共同占用区段；
 * ③ 查矩阵中两类作业是否 exclusive。三者同时成立才算冲突。
 * 禁止只比较设备/区段编号：重叠区段 + 矩阵允许 → 不冲突。
 */
@Service
public class MutexService {

    private final MutexRuleRepository mutexRuleRepository;

    public MutexService(MutexRuleRepository mutexRuleRepository) {
        this.mutexRuleRepository = mutexRuleRepository;
    }

    /**
     * 找出 candidate 与一组在期计划之间的矩阵冲突。
     */
    @Transactional(readOnly = true)
    public List<Conflict> findConflicts(WorkPlan candidate, List<WorkPlan> others) {
        return others.stream()
                .filter(other -> !other.getId().equals(candidate.getId()))
                .map(other -> evaluate(candidate, other))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * @return 冲突描述；矩阵允许或无共同区段时返回 empty
     */
    public Optional<Conflict> evaluate(WorkPlan a, WorkPlan b) {
        if (!overlaps(a, b)) {
            return Optional.empty();
        }
        Set<String> shared = new LinkedHashSet<>(a.sectionCodeSet());
        shared.retainAll(b.sectionCodeSet());
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        String typeA = normalize(a.getWorkType());
        String typeB = normalize(b.getWorkType());
        String low = typeA.compareTo(typeB) <= 0 ? typeA : typeB;
        String high = typeA.compareTo(typeB) <= 0 ? typeB : typeA;

        Optional<MutexRule> rule = mutexRuleRepository.findPair(low, high);
        // 矩阵没有明确条目时，按保守原则视为冲突，并说明原因是矩阵缺失，而非设备编号相同。
        boolean exclusive = rule.map(MutexRule::isExclusive).orElse(true);
        if (!exclusive) {
            return Optional.empty();
        }
        String reason = rule.map(r -> "互斥矩阵规定作业[" + a.getWorkType() + "]与[" + b.getWorkType()
                        + "] exclusive=true" + (r.getRemark() != null ? "（" + r.getRemark() + "）" : ""))
                .orElseGet(() -> "互斥作业矩阵缺少[" + a.getWorkType() + "]×[" + b.getWorkType()
                        + "]条目，按保守原则拒绝，请先在矩阵中登记");
        return Optional.of(new Conflict(b, List.copyOf(shared), reason));
    }

    /** 半开区间重叠：[windowStart, windowEnd)，首尾相接不算重叠。 */
    private boolean overlaps(WorkPlan a, WorkPlan b) {
        return a.getWindowStart().isBefore(b.getWindowEnd())
                && b.getWindowStart().isBefore(a.getWindowEnd());
    }

    private String normalize(String workType) {
        return workType == null ? "" : workType.trim().toUpperCase();
    }

    /**
     * 冲突详情：与哪个计划、在哪些区段、矩阵依据是什么。
     */
    public record Conflict(WorkPlan otherPlan, List<String> sharedSections, String matrixReason) {
    }
}
