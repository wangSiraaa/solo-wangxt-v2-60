package com.railwindow.sim.domain;

/**
 * 确认记录类型（确认链）。
 */
public enum ConfirmationKind {
    /** 区段保护确认（封锁防护、信号/道岔防护到位）。 */
    PROTECTION,
    /** 开工确认（开工许可回执落库）。 */
    START,
    /** 销记前的逐项复核/撤离确认。 */
    CLOSEOUT_ITEM,
    /** 销记（解除封锁、恢复行车）。 */
    CLOSEOUT
}
