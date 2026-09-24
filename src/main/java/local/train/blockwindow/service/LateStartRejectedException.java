package local.train.blockwindow.service;

/**
 * 迟到开工消息被拒（计划已销记）。映射 HTTP 409 之外的业务语义码，
 * 便于演练中学员区分“前置条件不满足”和“迟到消息”。
 */
public class LateStartRejectedException extends RuntimeException {
    public LateStartRejectedException(String message) {
        super(message);
    }
}
