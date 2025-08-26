package io.hhplus.tdd.point.lock;

import io.hhplus.tdd.point.exception.LockAcquisitionException;
import io.hhplus.tdd.point.exception.PointConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LockManager 테스트")
class LockManagerTest {

    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new LockManager();
    }

    @Test
    @DisplayName("단일 유저 락 획득 및 해제 성공")
    void executeWithLock_단일유저_성공() {
        // given
        Long userId = 1L;
        AtomicInteger counter = new AtomicInteger(0);

        // when
        String result = lockManager.executeWithLock(userId, "TEST", () -> {
            counter.incrementAndGet();
            return "success";
        });

        // then
        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(1);
        assertThat(lockManager.isLocked(userId)).isFalse();
    }

    @Test
    @DisplayName("반환값이 없는 작업 실행 성공")
    void executeWithLock_Runnable_성공() {
        // given
        Long userId = 1L;
        AtomicInteger counter = new AtomicInteger(0);

        // when
        lockManager.executeWithLock(userId, () -> {
            counter.incrementAndGet();
        });

        // then
        assertThat(counter.get()).isEqualTo(1);
        assertThat(lockManager.isLocked(userId)).isFalse();
    }

    @Test
    @DisplayName("동일 유저 동시 액세스 시 순차 실행")
    @Timeout(10)
    void executeWithLock_동일유저_순차실행() throws InterruptedException {
        // given
        Long userId = 1L;
        int threadCount = 5;
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드 동시 시작
                    
                    lockManager.executeWithLock(userId, "TEST", () -> {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));
                        
                        try {
                            Thread.sleep(100); // 작업 시뮬레이션
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        counter.incrementAndGet();
                        currentConcurrent.decrementAndGet();
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 시작
        doneLatch.await(10, TimeUnit.SECONDS); // 완료 대기
        executor.shutdown();

        // then
        assertThat(counter.get()).isEqualTo(threadCount);
        assertThat(maxConcurrent.get()).isEqualTo(1); // 동시 실행은 1개만
        assertThat(lockManager.isLocked(userId)).isFalse();
    }

    @Test
    @DisplayName("서로 다른 유저는 동시 실행 가능")
    @Timeout(10)
    void executeWithLock_다른유저_동시실행() throws InterruptedException {
        // given
        int userCount = 3;
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        // when
        for (int i = 1; i <= userCount; i++) {
            Long userId = (long) i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    lockManager.executeWithLock(userId, "TEST", () -> {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));
                        
                        try {
                            Thread.sleep(200); // 작업 시뮬레이션
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        counter.incrementAndGet();
                        currentConcurrent.decrementAndGet();
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(counter.get()).isEqualTo(userCount);
        assertThat(maxConcurrent.get()).isEqualTo(userCount); // 모든 유저 동시 실행
    }

    @Test
    @DisplayName("작업 중 예외 발생 시 락 해제")
    void executeWithLock_예외발생시_락해제() {
        // given
        Long userId = 1L;
        RuntimeException testException = new RuntimeException("테스트 예외");

        // when & then
        assertThatThrownBy(() -> {
            lockManager.executeWithLock(userId, "TEST", () -> {
                throw testException;
            });
        }).isInstanceOf(PointConcurrencyException.class)
          .hasCauseReference(testException);

        // 락이 해제되었는지 확인
        assertThat(lockManager.isLocked(userId)).isFalse();
        
        // 다음 요청이 정상 처리되는지 확인
        String result = lockManager.executeWithLock(userId, "TEST", () -> "success");
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("락 상태 조회 기능")
    void lockStatus_조회_성공() {
        // given
        Long userId = 1L;

        // when & then - 락 없는 상태
        assertThat(lockManager.isLocked(userId)).isFalse();
        assertThat(lockManager.getQueueLength(userId)).isEqualTo(0);
        
        // 락 상태 문자열 확인
        String status = lockManager.getLockStatus(userId);
        assertThat(status).contains("락 없음");
    }

    @Test
    @DisplayName("활성 락 개수 조회")
    void getActiveLocksCount_조회_성공() throws InterruptedException {
        // given
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch lockReleased = new CountDownLatch(1);
        
        // when
        Thread lockThread = new Thread(() -> {
            lockManager.executeWithLock(1L, () -> {
                lockHeld.countDown(); // 락 획득 알림
                try {
                    lockReleased.await(); // 해제 신호 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        
        lockThread.start();
        lockHeld.await(); // 락이 획득될 때까지 대기

        // then
        assertThat(lockManager.getActiveLocksCount()).isEqualTo(1);
        assertThat(lockManager.isLocked(1L)).isTrue();
        
        // 락 해제
        lockReleased.countDown();
        lockThread.join(1000);
        
        assertThat(lockManager.getActiveLocksCount()).isEqualTo(0);
        assertThat(lockManager.isLocked(1L)).isFalse();
    }

    @Test
    @DisplayName("스레드 인터럽트 처리")
    void executeWithLock_인터럽트_예외발생() throws InterruptedException {
        // given
        Long userId = 1L;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        
        // 먼저 락을 획득하는 스레드
        Thread lockHolder = new Thread(() -> {
            lockManager.executeWithLock(userId, () -> {
                lockAcquired.countDown();
                try {
                    Thread.sleep(10000); // 10초 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        lockHolder.start();
        lockAcquired.await(); // 락 획득 대기

        // when & then - 대기중인 스레드를 인터럽트
        Thread waitingThread = new Thread(() -> {
            assertThatThrownBy(() -> {
                lockManager.executeWithLock(userId, () -> "should not execute");
            }).isInstanceOf(LockAcquisitionException.class);
        });
        
        waitingThread.start();
        Thread.sleep(100); // 대기 상태가 되도록 잠시 대기
        waitingThread.interrupt(); // 인터럽트
        waitingThread.join(1000);
        
        // cleanup
        lockHolder.interrupt();
        lockHolder.join(1000);
    }
}