package local.train.blockwindow.web;

import local.train.blockwindow.service.LateStartRejectedException;
import local.train.blockwindow.service.NotFoundException;
import local.train.blockwindow.service.PreconditionNotMetException;
import local.train.blockwindow.service.ApiConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 统一异常出参：开工/销记失败必须把逐项缺失条件返回，
 * 禁止用一个“审批失败”抹掉具体原因。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PreconditionNotMetException.class)
    public ResponseEntity<Map<String, Object>> handlePrecondition(PreconditionNotMetException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", ex.getErrorCode(),
                "message", ex.getMessage(),
                "missingConditions", ex.getMissingConditions()
        ));
    }

    @ExceptionHandler(LateStartRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleLate(LateStartRejectedException ex) {
        // 409 + 专用错误码：迟到消息被拒，计划状态未变更
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", "LATE_START_AFTER_RELEASE",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", "NOT_FOUND",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(ApiConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ApiConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", "CONFLICT",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            DeadlockLoserDataAccessException.class})
    public ResponseEntity<Map<String, Object>> handleConcurrentLock(Exception ex) {
        // 两个计划竞争开工时的并发兜底：行锁竞争/死锁由数据库裁决，败者得到明确提示（而非审批失败/500）
        log.info("并发锁竞争，需重新评估: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", "CONCURRENT_CONFLICT",
                "message", "并发竞争：计划正被另一笔开工事务锁定，请重新读取并依据互斥矩阵重新评估后重试"
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", "VALIDATION_FAILED",
                "message", "请求参数校验未通过",
                "fields", fields
        ));
    }
}
