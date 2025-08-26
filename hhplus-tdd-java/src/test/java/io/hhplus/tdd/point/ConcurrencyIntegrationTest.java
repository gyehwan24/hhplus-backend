package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 서비스의 동시성 제어 통합 테스트
 * 실제 컴포넌트들을 사용하여 동시성 문제가 해결되었는지 검증
 */
@SpringBootTest
@DisplayName("동시성 통합 테스트")
class ConcurrencyIntegrationTest {

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;
    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        // 실제 구현체들을 사용 (의존성 주입 대신 직접 생성)
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        lockManager = new LockManager();
        pointService = new PointService(pointHistoryTable, userPointTable, lockManager);
        
        // 테스트 유저 초기화 (1000포인트로 시작)
        userPointTable.insertOrUpdate(1L, 1000L);
        userPointTable.insertOrUpdate(2L, 1000L);
        userPointTable.insertOrUpdate(3L, 1000L);
    }

    @Test
    @DisplayName("동일 유저의 동시 충전 요청 - 순차 처리 보장")
    @Timeout(30)
    void concurrent_charge_same_user_sequential_processing() throws InterruptedException {
        // given
        Long userId = 1L;
        int threadCount = 10;
        long chargeAmount = 100L;
        long initialPoint = userPointTable.selectById(userId).point();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // when - 동시에 충전 요청 실행
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.charge(userId, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생시에도 대기 해제
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        
        // then - 모든 충전이 성공하고 포인트가 정확해야 함
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedPoint = initialPoint + (chargeAmount * threadCount);
        
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(finalPoint.point()).isEqualTo(expectedPoint);
        
        // 이력도 정확해야 함
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        long chargeHistories = histories.stream()
                .filter(h -> h.type() == TransactionType.CHARGE)
                .count();
        assertThat(chargeHistories).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("동일 유저의 동시 사용 요청 - 순차 처리 보장")
    @Timeout(30)
    void concurrent_use_same_user_sequential_processing() throws InterruptedException {
        // given
        Long userId = 2L;
        int threadCount = 5;
        long useAmount = 100L;
        long initialPoint = userPointTable.selectById(userId).point();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.use(userId, useAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족 등의 예외는 정상적인 상황
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        
        // then
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedPoint = initialPoint - (useAmount * successCount.get());
        
        assertThat(finalPoint.point()).isEqualTo(expectedPoint);
        assertThat(successCount.get()).isLessThanOrEqualTo(threadCount);
        
        // 이력 검증
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        long useHistories = histories.stream()
                .filter(h -> h.type() == TransactionType.USE)
                .count();
        assertThat(useHistories).isEqualTo(successCount.get());
    }

    @Test
    @DisplayName("동일 유저의 충전/사용 혼합 요청 - 데이터 일관성 보장")
    @Timeout(30)
    void concurrent_mixed_operations_same_user_data_consistency() throws InterruptedException {
        // given
        Long userId = 3L;
        int operationCount = 20;
        long amount = 50L;
        long initialPoint = userPointTable.selectById(userId).point();
        
        ExecutorService executor = Executors.newFixedThreadPool(operationCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(operationCount);
        AtomicInteger chargeCount = new AtomicInteger(0);
        AtomicInteger useCount = new AtomicInteger(0);
        
        // when - 충전과 사용을 번갈아 가며 실행
        for (int i = 0; i < operationCount; i++) {
            final boolean isCharge = i % 2 == 0;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (isCharge) {
                        pointService.charge(userId, amount);
                        chargeCount.incrementAndGet();
                    } else {
                        pointService.use(userId, amount);
                        useCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 잔액 부족 등의 예외는 정상적인 상황
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        
        // then
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedMinPoint = initialPoint + (amount * chargeCount.get()) - (amount * useCount.get());
        
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0); // 음수가 되면 안됨
        assertThat(finalPoint.point()).isEqualTo(expectedMinPoint);
        
        // 이력 검증
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        long totalOperations = chargeCount.get() + useCount.get();
        assertThat(histories).hasSize((int) totalOperations);
    }

    @Test
    @DisplayName("서로 다른 유저의 동시 요청 - 독립적 처리")
    @Timeout(30)
    void concurrent_different_users_independent_processing() throws InterruptedException {
        // given
        int userCount = 5;
        int operationsPerUser = 10;
        long chargeAmount = 100L;
        
        ExecutorService executor = Executors.newFixedThreadPool(userCount * operationsPerUser);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount * operationsPerUser);
        
        // 유저별 초기 포인트 설정
        for (int userId = 10; userId < 10 + userCount; userId++) {
            userPointTable.insertOrUpdate((long) userId, 500L);
        }
        
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        // when - 각 유저별로 동시에 충전 요청 실행
        for (int userId = 10; userId < 10 + userCount; userId++) {
            for (int op = 0; op < operationsPerUser; op++) {
                final Long currentUserId = (long) userId;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long startTime = System.currentTimeMillis();
                        pointService.charge(currentUserId, chargeAmount);
                        long endTime = System.currentTimeMillis();
                        totalProcessingTime.addAndGet(endTime - startTime);
                    } catch (Exception e) {
                        // 예외 무시
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }
        
        long overallStartTime = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long overallEndTime = System.currentTimeMillis();
        executor.shutdown();
        
        // then - 각 유저의 포인트가 정확해야 함
        for (int userId = 10; userId < 10 + userCount; userId++) {
            UserPoint userPoint = userPointTable.selectById((long) userId);
            long expectedPoint = 500L + (chargeAmount * operationsPerUser);
            assertThat(userPoint.point()).isEqualTo(expectedPoint);
        }
        
        // 동시 처리로 인한 성능 향상 확인 (단순 합계 시간보다 실제 시간이 적어야 함)
        long overallTime = overallEndTime - overallStartTime;
        assertThat(overallTime).isLessThan(totalProcessingTime.get() / 2);
    }

    @Test
    @DisplayName("동시성 제어의 안정성 검증")
    @Timeout(30)
    void concurrency_control_stability_verification() throws InterruptedException {
        // given
        Long userId = 100L;
        userPointTable.insertOrUpdate(userId, 5000L);
        
        int chargeThreads = 10;
        int useThreads = 5;
        long chargeAmount = 100L;
        long useAmount = 200L;
        
        ExecutorService executor = Executors.newFixedThreadPool(chargeThreads + useThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(chargeThreads + useThreads);
        
        AtomicInteger successfulCharges = new AtomicInteger(0);
        AtomicInteger successfulUses = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        
        // when - 충전 스레드들 실행
        for (int i = 0; i < chargeThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.charge(userId, chargeAmount);
                    successfulCharges.incrementAndGet();
                } catch (Exception e) {
                    failedOperations.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // 사용 스레드들 실행
        for (int i = 0; i < useThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.use(userId, useAmount);
                    successfulUses.incrementAndGet();
                } catch (Exception e) {
                    failedOperations.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(25, TimeUnit.SECONDS);
        executor.shutdown();
        
        // then
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedMinPoint = 5000L + (chargeAmount * successfulCharges.get()) - (useAmount * successfulUses.get());
        
        // 포인트 계산이 정확해야 함
        assertThat(finalPoint.point()).isEqualTo(expectedMinPoint);
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0);
        
        // 이력이 정확히 기록되어야 함
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        int expectedHistoryCount = successfulCharges.get() + successfulUses.get();
        assertThat(histories).hasSize(expectedHistoryCount);
        
        // 충전/사용 이력 개수 검증
        long chargeHistories = histories.stream().filter(h -> h.type() == TransactionType.CHARGE).count();
        long useHistories = histories.stream().filter(h -> h.type() == TransactionType.USE).count();
        assertThat(chargeHistories).isEqualTo(successfulCharges.get());
        assertThat(useHistories).isEqualTo(successfulUses.get());
        
        // 락이 모두 해제되어야 함
        assertThat(lockManager.isLocked(userId)).isFalse();
        assertThat(lockManager.getActiveLocksCount()).isZero();
    }

    @Test
    @DisplayName("포인트 조회 동시성 - 읽기 작업 중 데이터 일관성")
    @Timeout(30)
    void concurrent_point_inquiry_consistency() throws InterruptedException {
        // given
        Long userId = 200L;
        userPointTable.insertOrUpdate(userId, 5000L);
        
        int readerCount = 20;
        int writerCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + writerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerCount + writerCount);
        
        ConcurrentLinkedQueue<Long> readPoints = new ConcurrentLinkedQueue<>();
        AtomicInteger writeOperations = new AtomicInteger(0);
        
        // when - 읽기와 쓰기 작업을 동시에 실행
        
        // Reader 스레드들
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        UserPoint point = pointService.getPoint(userId);
                        readPoints.offer(point.point());
                        Thread.sleep(5); // 읽기 간격
                    }
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Writer 스레드들
        for (int i = 0; i < writerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 5; j++) {
                        pointService.charge(userId, 100L);
                        writeOperations.incrementAndGet();
                        Thread.sleep(20); // 쓰기 간격
                    }
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(25, TimeUnit.SECONDS);
        executor.shutdown();
        
        // then - 읽은 모든 포인트 값이 유효해야 함
        assertThat(readPoints).isNotEmpty();
        
        // 읽은 포인트들이 모두 논리적으로 일관성이 있어야 함 (5000 이상)
        for (Long point : readPoints) {
            assertThat(point).isGreaterThanOrEqualTo(5000L);
            assertThat(point).isLessThanOrEqualTo(5000L + (100L * writerCount * 5)); // 최대 가능한 값
        }
        
        // 최종 포인트가 정확해야 함
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedFinalPoint = 5000L + (100L * writeOperations.get());
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);
    }
}