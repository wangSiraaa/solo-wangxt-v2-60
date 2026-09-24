package com.railwindow.sim.domain;

/**
 * 模拟调度回执类型。
 */
public enum ReceiptKind {
    /** 排定窗口的调度受理回执。 */
    SCHEDULE,
    /** 重新安排窗口的调度受理回执。 */
    RESCHEDULE,
    /** 开工许可回执（封锁生效）。 */
    START,
    /** 销记回执（解除封锁、恢复行车）。 */
    CLOSEOUT
}
