package local.train.blockwindow.web;

import java.util.Map;

/**
 * 单项缺失条件。开工/销记被拒绝时必须逐项返回，禁止笼统返回“审批失败”。
 *
 * @param code    机器可读条件码，如 PROTECTION_NOT_CONFIRMED
 * @param message 面向演练学员的中文说明
 * @param detail  附加上下文（区段、人员证件号、冲突计划号等）
 */
public record MissingCondition(String code, String message, Map<String, Object> detail) {

    public static MissingCondition of(String code, String message, Map<String, Object> detail) {
        return new MissingCondition(code, message, detail);
    }
}
