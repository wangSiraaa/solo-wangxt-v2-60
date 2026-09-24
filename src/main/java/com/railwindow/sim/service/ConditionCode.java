package com.railwindow.sim.service;

/**
 * 开工/排定等动作被拒绝时的具体条件代码——用于向计划员逐项说明缺失条件，
 * 而不是笼统地返回“审批失败”。
 */
public enum ConditionCode {
    WINDOW_NOT_OPEN("封锁窗口尚未到开放时刻"),
    WINDOW_EXPIRED("封锁窗口已关闭（开工消息迟到）"),
    PROTECTION_NOT_READY("存在未确认保护到位的区段"),
    PERSONNEL_NOT_ARRIVED("存在未到岗人员"),
    QUALIFICATION_MISSING("缺少对应作业类型的资质"),
    QUALIFICATION_EXPIRES_BEFORE_FINISH("资质在预计结束时刻之前失效，需重新安排"),
    MUTEX_CONFLICT("按互斥作业矩阵判定存在冲突作业"),
    NOT_SCHEDULED("计划尚未排定，不能开工"),
    PLAN_SUPERSEDED("计划已被重新安排产生的新计划接替"),
    PLAN_ALREADY_ACTIVE("计划已开工"),
    PLAN_ALREADY_CLOSED("计划已销记，迟到的开工消息不得使其重新开工"),
    CLOSEOUT_ITEMS_PENDING("销记清单仍有未逐项确认项"),
    SECTIONS_NOT_ADJACENT("计划占用的多个区段不相邻"),
    INVALID_WINDOW("封锁窗口区间非法"),
    SECTION_NOT_FOUND("区段不存在"),
    PERSON_NOT_FOUND("人员不存在"),
    ILLEGAL_STATE("计划当前状态不允许该操作");

    private final String defaultMessage;

    ConditionCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
