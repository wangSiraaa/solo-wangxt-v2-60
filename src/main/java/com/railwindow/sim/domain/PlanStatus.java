package com.railwindow.sim.domain;

/**
 * 计划生命周期：DRAFT → SCHEDULED → ACTIVE → CLOSED。
 * RESCHEDULED 仅作为“重新安排”动作的结果标记：原计划作废，新计划继承其内容。
 */
public enum PlanStatus {
    DRAFT,
    SCHEDULED,
    ACTIVE,
    CLOSED,
    RESCHEDULED
}
