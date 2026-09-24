package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 互斥作业矩阵条目维护。exclusive=false 明确表示两类作业可以并行
 * （即“窗口重叠不一定冲突”的依据来自矩阵本身，而不是设备编号）。
 */
public record MutexRuleRequest(
        @NotBlank String workTypeA,
        @NotBlank String workTypeB,
        boolean exclusive,
        String remark) {
}
