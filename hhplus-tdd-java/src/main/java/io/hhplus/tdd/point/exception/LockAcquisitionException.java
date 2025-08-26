package io.hhplus.tdd.point.exception;

/**
 * 락 획득에 실패했을 때 발생하는 예외
 * 타임아웃, 인터럽트, 또는 기타 락 관련 문제 시 발생
 */
public class LockAcquisitionException extends RuntimeException {
    
    private final Long userId;
    private final long timeoutMs;
    private final String operation;
    
    public LockAcquisitionException(Long userId, long timeoutMs, String operation) {
        super(String.format("락 획득 실패: userId=%d, timeout=%dms, operation=%s", 
                           userId, timeoutMs, operation));
        this.userId = userId;
        this.timeoutMs = timeoutMs;
        this.operation = operation;
    }
    
    public LockAcquisitionException(Long userId, long timeoutMs, String operation, Throwable cause) {
        super(String.format("락 획득 실패: userId=%d, timeout=%dms, operation=%s", 
                           userId, timeoutMs, operation), cause);
        this.userId = userId;
        this.timeoutMs = timeoutMs;
        this.operation = operation;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public String getOperation() {
        return operation;
    }
}