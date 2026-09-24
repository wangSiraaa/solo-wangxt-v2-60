package local.train.blockwindow.service;

import local.train.blockwindow.web.MissingCondition;

import java.util.List;

/**
 * 开工/销记前置条件不满足。携带逐项缺失条件，由异常处理器原样返回给调用方。
 * 这是演练服务的重要设计：学员必须看到“具体缺什么”，而不是统一的审批失败。
 */
public class PreconditionNotMetException extends RuntimeException {

    private final String errorCode;
    private final transient List<MissingCondition> missingConditions;

    public PreconditionNotMetException(String errorCode, String message, List<MissingCondition> missingConditions) {
        super(message);
        this.errorCode = errorCode;
        this.missingConditions = List.copyOf(missingConditions);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<MissingCondition> getMissingConditions() {
        return missingConditions;
    }
}
