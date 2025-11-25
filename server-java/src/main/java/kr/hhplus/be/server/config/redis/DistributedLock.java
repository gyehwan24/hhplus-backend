package kr.hhplus.be.server.config.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 분산락 어노테이션
 *
 * 메서드에 선언하여 Redis 기반 분산락을 적용합니다.
 *
 * 사용 예시:
 * <pre>
 * {@code
 * @DistributedLock(key = "'scheduler:token:activate'", leaseTime = 30)
 * public int activateWaitingTokens() {
 *     // 비즈니스 로직
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 표현식 지원)
     *
     * 예시:
     * - {@code "'scheduler:token:activate'"} - 고정 문자열
     * - {@code "'user:charge:' + #userId"} - 파라미터 조합
     */
    String key();

    /**
     * 락 대기 시간 (초)
     *
     * 락 획득을 위해 최대 대기할 시간입니다.
     * 이 시간 내에 락을 획득하지 못하면 LockAcquisitionException이 발생합니다.
     *
     * 기본값: 5초
     */
    long waitTime() default 5L;

    /**
     * 락 유지 시간 (초)
     *
     * 락을 자동으로 해제할 시간입니다.
     * 데드락 방지를 위해 설정합니다.
     *
     * 기본값: 10초
     */
    long leaseTime() default 10L;
}
