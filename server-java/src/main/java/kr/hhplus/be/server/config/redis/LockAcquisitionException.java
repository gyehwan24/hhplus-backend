package kr.hhplus.be.server.config.redis;

/**
 * 분산락 획득 실패 예외
 *
 * 지정된 waitTime 내에 락을 획득하지 못했을 때 발생합니다.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
