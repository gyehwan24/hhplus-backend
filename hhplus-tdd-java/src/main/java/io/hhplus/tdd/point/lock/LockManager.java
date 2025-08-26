package io.hhplus.tdd.point.lock;

import io.hhplus.tdd.point.exception.LockAcquisitionException;
import io.hhplus.tdd.point.exception.PointConcurrencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 포인트 시스템의 동시성 제어를 위한 락 관리자
 * 유저별로 독립적인 락을 관리하여 동시성 문제를 해결
 */
@Component
public class LockManager {
    
    private static final Logger log = LoggerFactory.getLogger(LockManager.class);
    
    // 타임아웃 및 재시도 상수
    private static final long DEFAULT_LOCK_TIMEOUT_MS = 5000; // 5초
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;
    
    // 유저별 락 관리 (공정성 모드로 생성)
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    
    /**
     * 반환값이 없는 작업을 락과 함께 실행
     * 
     * @param userId 사용자 ID
     * @param task 실행할 작업
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public void executeWithLock(Long userId, Runnable task) {
        executeWithLock(userId, () -> {
            task.run();
            return null;
        });
    }
    
    /**
     * 반환값이 있는 작업을 락과 함께 실행
     * 
     * @param userId 사용자 ID  
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public <T> T executeWithLock(Long userId, Supplier<T> task) {
        return executeWithLockAndRetry(userId, task, "UNKNOWN", 0);
    }
    
    /**
     * 작업명을 포함하여 락과 함께 실행 (로깅 및 디버깅용)
     */
    public <T> T executeWithLock(Long userId, String operation, Supplier<T> task) {
        return executeWithLockAndRetry(userId, task, operation, 0);
    }
    
    /**
     * 재시도 로직을 포함한 락 실행
     */
    private <T> T executeWithLockAndRetry(Long userId, Supplier<T> task, String operation, int retryCount) {
        ReentrantLock lock = getUserLock(userId);
        long startTime = System.currentTimeMillis();
        
        try {
            // 락 획득 시도 (타임아웃 적용)
            if (lock.tryLock(DEFAULT_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                try {
                    long lockAcquiredTime = System.currentTimeMillis();
                    long waitTime = lockAcquiredTime - startTime;
                    
                    if (waitTime > 1000) { // 1초 이상 대기한 경우 로깅
                        log.warn("락 획득에 {}ms 소요: userId={}, operation={}", waitTime, userId, operation);
                    }
                    
                    // 실제 작업 실행
                    T result = task.get();
                    
                    long executionTime = System.currentTimeMillis() - lockAcquiredTime;
                    if (executionTime > 500) { // 0.5초 이상 실행된 경우 로깅
                        log.info("작업 실행 완료: userId={}, operation={}, executionTime={}ms", 
                                userId, operation, executionTime);
                    }
                    
                    return result;
                    
                } finally {
                    lock.unlock();
                }
            } else {
                // 락 획득 실패 - 재시도 로직
                if (retryCount < MAX_RETRY_COUNT) {
                    log.warn("락 획득 실패, 재시도 중: userId={}, operation={}, retryCount={}", 
                            userId, operation, retryCount + 1);
                    
                    // 백오프 대기
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (retryCount + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LockAcquisitionException(userId, DEFAULT_LOCK_TIMEOUT_MS, operation, e);
                    }
                    
                    return executeWithLockAndRetry(userId, task, operation, retryCount + 1);
                } else {
                    // 최대 재시도 횟수 초과
                    throw new LockAcquisitionException(userId, DEFAULT_LOCK_TIMEOUT_MS, operation);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(userId, DEFAULT_LOCK_TIMEOUT_MS, operation, e);
        } catch (Exception e) {
            // 예상치 못한 예외를 PointConcurrencyException으로 래핑
            if (e instanceof LockAcquisitionException) {
                throw e;
            }
            throw new PointConcurrencyException(userId, operation, 0, "락 실행 중 예상치 못한 오류", e);
        }
    }
    
    /**
     * 유저별 락을 가져오거나 생성
     */
    private ReentrantLock getUserLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true)); // 공정성 모드
    }
    
    /**
     * 특정 유저의 락이 현재 잠겨있는지 확인
     */
    public boolean isLocked(Long userId) {
        ReentrantLock lock = userLocks.get(userId);
        return lock != null && lock.isLocked();
    }
    
    /**
     * 현재 활성화된 락의 개수
     */
    public int getActiveLocksCount() {
        return (int) userLocks.values().stream()
                             .mapToInt(lock -> lock.isLocked() ? 1 : 0)
                             .sum();
    }
    
    /**
     * 특정 유저의 락 대기중인 스레드 수
     */
    public int getQueueLength(Long userId) {
        ReentrantLock lock = userLocks.get(userId);
        return lock != null ? lock.getQueueLength() : 0;
    }
    
    /**
     * 디버깅을 위한 락 상태 정보
     */
    public String getLockStatus(Long userId) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock == null) {
            return String.format("userId=%d: 락 없음", userId);
        }
        
        return String.format("userId=%d: locked=%s, holdCount=%d, queueLength=%d", 
                           userId, lock.isLocked(), lock.getHoldCount(), lock.getQueueLength());
    }
}