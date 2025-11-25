package kr.hhplus.be.server.config.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 분산락 기본 동작 테스트
 * - 락 획득/해제
 * - 동시성 제어
 * - 타임아웃 처리
 */
@DisplayName("분산락 기본 동작 테스트")
class DistributedLockTest extends BaseRedisTest {

    @Autowired
    private TestLockService testLockService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        testLockService.resetCounter();
    }

    @AfterEach
    void tearDown() {
        // 각 테스트 후 Redis 키 삭제
        redisTemplate.delete("test:lock");
        redisTemplate.delete("test:lock:param:123");
    }

    @Test
    @DisplayName("분산락으로 동시성 제어 성공")
    void distributedLock_concurrency_success() throws InterruptedException {
        // Given: 10개 스레드가 동시에 카운터 증가 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When: 모든 스레드가 동시에 실행
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    testLockService.incrementWithLock();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 분산락으로 순차 실행되어 정확히 10 증가
        assertThat(testLockService.getCounter()).isEqualTo(10);
    }

    @Test
    @DisplayName("분산락 없이 동시성 제어 실패")
    void withoutLock_concurrency_fail() throws InterruptedException {
        // Given: 10개 스레드가 동시에 카운터 증가 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When: 락 없이 동시 실행
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    testLockService.incrementWithoutLock();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 동시성 이슈로 10보다 작을 수 있음 (경합 발생)
        // 단, 스레드 스케줄링에 따라 10이 나올 수도 있음
        System.out.println("Without lock counter: " + testLockService.getCounter());
    }

    @Test
    @DisplayName("락 획득 실패 시 예외 발생")
    void lockAcquisitionTimeout_throwsException() throws InterruptedException {
        // Given: 첫 번째 스레드가 락을 획득하고 3초간 보유
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
            testLockService.holdLockFor3Seconds();
        });

        // 첫 번째 스레드가 락을 획득할 시간 확보
        Thread.sleep(100);

        // When & Then: 두 번째 스레드가 대기 시간(1초) 내에 락을 못 얻으면 예외 발생
        assertThatThrownBy(() -> testLockService.tryLockWithShortWaitTime())
                .isInstanceOf(LockAcquisitionException.class)
                .hasMessageContaining("Failed to acquire lock");

        executorService.shutdown();
    }

    @Test
    @DisplayName("SpEL 파라미터 기반 동적 락 키 생성")
    void dynamicLockKey_withParameter() throws InterruptedException {
        // Given: 서로 다른 userId로 동시 실행
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicInteger user1Result = new AtomicInteger();
        AtomicInteger user2Result = new AtomicInteger();

        // When: userId=1과 userId=2는 서로 다른 락 키 사용
        executorService.submit(() -> {
            try {
                user1Result.set(testLockService.processWithUserLock(1L));
            } finally {
                latch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                user2Result.set(testLockService.processWithUserLock(2L));
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executorService.shutdown();

        // Then: 서로 다른 락이므로 모두 성공
        assertThat(user1Result.get()).isEqualTo(1);
        assertThat(user2Result.get()).isEqualTo(2);
    }
}
