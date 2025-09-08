package io.hhplus.tdd.point.exception;

/**
 * 포인트 관련 동시성 문제가 발생했을 때 발생하는 예외
 * 락 관련 문제, 데이터 정합성 문제 등 포괄적인 동시성 문제를 나타냄
 */
public class PointConcurrencyException extends RuntimeException {
    
    private final Long userId;
    private final String operation;
    private final long requestedAmount;
    
    public PointConcurrencyException(Long userId, String operation, long requestedAmount, String message) {
        super(String.format("포인트 동시성 오류: userId=%d, operation=%s, amount=%d - %s", 
                           userId, operation, requestedAmount, message));
        this.userId = userId;
        this.operation = operation;
        this.requestedAmount = requestedAmount;
    }
    
    public PointConcurrencyException(Long userId, String operation, long requestedAmount, String message, Throwable cause) {
        super(String.format("포인트 동시성 오류: userId=%d, operation=%s, amount=%d - %s", 
                           userId, operation, requestedAmount, message), cause);
        this.userId = userId;
        this.operation = operation;
        this.requestedAmount = requestedAmount;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public long getRequestedAmount() {
        return requestedAmount;
    }
}