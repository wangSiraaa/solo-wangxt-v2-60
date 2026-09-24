package local.train.blockwindow.service;

/** 请求与当前状态冲突（重复建计划、重复派工等），映射 HTTP 409。 */
public class ApiConflictException extends RuntimeException {
    public ApiConflictException(String message) {
        super(message);
    }
}
