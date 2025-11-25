package kr.hhplus.be.server.config.redis;

import org.springframework.stereotype.Service;

/**
 * 테스트용 서비스
 * 분산락 동작을 검증하기 위한 간단한 카운터 서비스
 */
@Service
public class TestLockService {
    private int counter = 0;

    @DistributedLock(key = "'test:lock'", waitTime = 5, leaseTime = 10)
    public void incrementWithLock() {
        int current = counter;
        // 동시성 이슈를 확인하기 위해 의도적으로 sleep
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        counter = current + 1;
    }

    public void incrementWithoutLock() {
        int current = counter;
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        counter = current + 1;
    }

    @DistributedLock(key = "'test:lock'", waitTime = 3, leaseTime = 5)
    public void holdLockFor3Seconds() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DistributedLock(key = "'test:lock'", waitTime = 1, leaseTime = 5)
    public void tryLockWithShortWaitTime() {
        // 락 획득 실패 테스트용
    }

    @DistributedLock(key = "'test:lock:param:' + #userId", waitTime = 3, leaseTime = 5)
    public int processWithUserLock(Long userId) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return userId.intValue();
    }

    public int getCounter() {
        return counter;
    }

    public void resetCounter() {
        counter = 0;
    }
}
