package kr.hhplus.be.server.config.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 AOP
 *
 * Order=1로 설정하여 @Transactional(기본값 Integer.MAX_VALUE)보다 먼저 실행됩니다.
 * 실행 순서: 분산락 획득 → 트랜잭션 시작 → 비즈니스 로직 → 트랜잭션 커밋 → 분산락 해제
 *
 * Redisson 사용 이유:
 * - 자동 Watchdog: 비즈니스 로직 실행 중 락 자동 갱신
 * - Reentrant Lock: 같은 스레드 재진입 가능
 * - Pub/Sub 기반: 폴링 대신 이벤트 기반으로 효율적
 */
@Slf4j
@Aspect
@Order(1)
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 1. SpEL로 동적 락 키 생성
        String lockKey = generateLockKey(joinPoint, distributedLock.key());
        RLock lock = redissonClient.getLock(lockKey);

        long waitTime = distributedLock.waitTime();
        long leaseTime = distributedLock.leaseTime();

        log.debug("Attempting to acquire lock: key={}, waitTime={}s, leaseTime={}s",
                  lockKey, waitTime, leaseTime);

        boolean acquired = false;
        try {
            // 2. 락 획득 시도
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                throw new LockAcquisitionException(
                    String.format("Failed to acquire lock within %d seconds: %s", waitTime, lockKey)
                );
            }

            log.debug("Lock acquired: key={}", lockKey);

            // 3. 비즈니스 로직 실행 (Watchdog가 자동으로 락 갱신)
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Lock acquisition interrupted: " + lockKey, e);
        } finally {
            // 4. 락 해제 (자신이 획득한 락만 해제)
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL을 사용하여 동적 락 키 생성
     *
     * 예시:
     * - "'scheduler:token:activate'" → "scheduler:token:activate"
     * - "'lock:stock:' + #productId" → "lock:stock:100"
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // SpEL 컨텍스트에 메서드 파라미터 등록
        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // SpEL 파싱 및 평가
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
