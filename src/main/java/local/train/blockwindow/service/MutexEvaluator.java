package local.train.blockwindow.service;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.MutexRule;
import local.train.blockwindow.repo.MutexRuleRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 互斥作业矩阵评估。
 *
 * <p>判定冲突必须同时满足三个条件：
 * <ol>
 *   <li>时间区间重叠（半开区间，首尾相接不算）；</li>
 *   <li>两个计划占用区段存在交集（多区段计划逐区段求交，<b>禁止只比单个设备编号</b>）；</li>
 *   <li>两个作业类型命中 {@code mutex_rule} 互斥矩阵（双向匹配）。</li>
 * </ol>
 * 矩阵空白意味着兼容：例如电缆测试与日常巡视即使时间重叠、区段相同也允许并行。
 */
@Component
public class MutexEvaluator {

    private final MutexRuleRepository mutexRuleRepository;

    public MutexEvaluator(MutexRuleRepository mutexRuleRepository) {
        this.mutexRuleRepository = mutexRuleRepository;
    }

    /** 返回命中的矩阵条目；未命中（允许并行）返回 null。 */
    public MutexHit evaluate(BlockWindow a, BlockWindow b) {
        if (a.getId().equals(b.getId())) {
            return null;
        }
        if (!a.overlaps(b.getPlannedStart(), b.getPlannedEnd())) {
            return null;
        }
        List<String> shared = a.getSectionCodes().stream()
                .filter(b.getSectionCodes()::contains)
                .distinct()
                .sorted()
                .toList();
        if (shared.isEmpty()) {
            return null;
        }
        // 只有时间重叠 AND 区段相交 AND 命中互斥矩阵，才算冲突
        return mutexRuleRepository.findAll().stream()
                .filter(rule -> rule.matches(a.getWorkType(), b.getWorkType()))
                .findFirst()
                .map(rule -> new MutexHit(b, shared, rule))
                .orElse(null);
    }

    /**
     * @param other          与之冲突的在途计划
     * @param sharedSections 实际相交的区段（不是编号相等即冲突，必须落到矩阵判定）
     * @param rule           命中的矩阵条目
     */
    public record MutexHit(BlockWindow other, List<String> sharedSections, MutexRule rule) {
    }
}
