package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 포인트 시스템의 성능 측정 테스트
 */
@SpringBootTest
@DisplayName("성능 측정 테스트")
class PerformanceTest {

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;
    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        lockManager = new LockManager();
        pointService = new PointService(pointHistoryTable, userPointTable, lockManager);
    }

    @Test
    @DisplayName("단일 유저 처리 성능 측정")
    void single_user_performance_test() throws InterruptedException {
        // given
        Long userId = 1L;
        userPointTable.insertOrUpdate(userId, 10000L);
        
        int operationCount = 100;
        long chargeAmount = 100L;
        
        // when - 성능 측정
        long startTime = System.nanoTime();
        
        for (int i = 0; i < operationCount; i++) {
            pointService.charge(userId, chargeAmount);
        }
        
        long endTime = System.nanoTime();
        
        // then - 결과 분석
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeMs = (double) totalTimeMs / operationCount;
        double tps = 1000.0 / avgTimeMs;
        
        System.out.println("=== 단일 유저 성능 측정 결과 ===");
        System.out.printf("총 실행시간: %d ms%n", totalTimeMs);
        System.out.printf("평균 처리시간: %.2f ms/작업%n", avgTimeMs);
        System.out.printf("초당 처리량(TPS): %.0f%n", tps);
        
        // 최종 포인트 검증
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expectedPoint = 10000L + (chargeAmount * operationCount);
        System.out.printf("포인트 정확성: %s (기대: %d, 실제: %d)%n", 
                finalPoint.point() == expectedPoint ? "✅" : "❌", expectedPoint, finalPoint.point());
    }

    @Test
    @DisplayName("다중 유저 동시 처리 성능 측정")
    void multi_user_concurrent_performance_test() throws InterruptedException {
        // given
        int userCount = 5;
        int operationsPerUser = 20;
        long chargeAmount = 100L;
        
        // 유저 초기화
        for (int i = 1; i <= userCount; i++) {
            userPointTable.insertOrUpdate((long) i, 5000L);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        
        List<Long> executionTimes = new ArrayList<>();
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        // when - 동시 처리 성능 측정
        for (int userId = 1; userId <= userCount; userId++) {
            final Long currentUserId = (long) userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    long userStartTime = System.nanoTime();
                    
                    for (int op = 0; op < operationsPerUser; op++) {
                        long opStartTime = System.nanoTime();
                        pointService.charge(currentUserId, chargeAmount);
                        long opEndTime = System.nanoTime();
                        
                        synchronized (executionTimes) {
                            executionTimes.add((opEndTime - opStartTime) / 1_000_000);
                        }
                    }
                    
                    long userEndTime = System.nanoTime();
                    totalProcessingTime.addAndGet((userEndTime - userStartTime) / 1_000_000);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        long overallStartTime = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long overallEndTime = System.nanoTime();
        
        executor.shutdown();
        
        // then - 결과 분석
        long overallTimeMs = (overallEndTime - overallStartTime) / 1_000_000;
        double avgOperationTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        int totalOperations = userCount * operationsPerUser;
        double overallTps = (totalOperations * 1000.0) / overallTimeMs;
        
        System.out.println("=== 다중 유저 동시 처리 성능 측정 결과 ===");
        System.out.printf("전체 실행시간: %d ms%n", overallTimeMs);
        System.out.printf("총 처리 시간 합계: %d ms%n", totalProcessingTime.get());
        System.out.printf("평균 작업 처리시간: %.2f ms%n", avgOperationTime);
        System.out.printf("전체 처리량(TPS): %.0f%n", overallTps);
        System.out.printf("동시성 효율: %.1fx (순차 대비)%n", (double) totalProcessingTime.get() / overallTimeMs);
        
        // 각 유저별 포인트 정확성 검증
        for (int userId = 1; userId <= userCount; userId++) {
            UserPoint userPoint = userPointTable.selectById((long) userId);
            long expectedPoint = 5000L + (chargeAmount * operationsPerUser);
            System.out.printf("User %d 포인트: %s (기대: %d, 실제: %d)%n", 
                    userId, userPoint.point() == expectedPoint ? "✅" : "❌", expectedPoint, userPoint.point());
        }
    }

    @Test
    @DisplayName("락 획득 지연시간 측정")
    void lock_acquisition_latency_test() throws InterruptedException {
        // given
        Long userId = 100L;
        userPointTable.insertOrUpdate(userId, 10000L);
        
        int threadCount = 10;
        int operationsPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        List<Long> lockAcquisitionTimes = new ArrayList<>();
        List<Long> executionTimes = new ArrayList<>();
        
        // when - 락 경합 상황에서 지연시간 측정
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int op = 0; op < operationsPerThread; op++) {
                        long requestStart = System.nanoTime();
                        
                        // LockManager의 executeWithLock 호출하여 락 획득 시간 측정
                        lockManager.executeWithLock(userId, "PERFORMANCE_TEST", () -> {
                            long lockAcquired = System.nanoTime();
                            synchronized (lockAcquisitionTimes) {
                                lockAcquisitionTimes.add((lockAcquired - requestStart) / 1_000_000);
                            }
                            
                            // 실제 작업 시뮬레이션
                            try {
                                Thread.sleep(5); // 5ms 작업 시뮬레이션
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            long workComplete = System.nanoTime();
                            synchronized (executionTimes) {
                                executionTimes.add((workComplete - lockAcquired) / 1_000_000);
                            }
                            
                            return null;
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();
        
        // then - 지연시간 분석
        double avgLockTime = lockAcquisitionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double avgExecutionTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxLockTime = lockAcquisitionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minLockTime = lockAcquisitionTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        
        // 95% 백분위수 계산
        List<Long> sortedLockTimes = lockAcquisitionTimes.stream().sorted().toList();
        long p95LockTime = sortedLockTimes.get((int) (sortedLockTimes.size() * 0.95));
        long p99LockTime = sortedLockTimes.get((int) (sortedLockTimes.size() * 0.99));
        
        System.out.println("=== 락 획득 지연시간 측정 결과 ===");
        System.out.printf("총 측정 샘플: %d개%n", lockAcquisitionTimes.size());
        System.out.printf("평균 락 획득시간: %.2f ms%n", avgLockTime);
        System.out.printf("최소 락 획득시간: %d ms%n", minLockTime);
        System.out.printf("최대 락 획득시간: %d ms%n", maxLockTime);
        System.out.printf("95%% 지연시간: %d ms%n", p95LockTime);
        System.out.printf("99%% 지연시간: %d ms%n", p99LockTime);
        System.out.printf("평균 작업 실행시간: %.2f ms%n", avgExecutionTime);
        
        // 락이 모두 해제되었는지 확인
        System.out.printf("최종 락 상태: %s%n", lockManager.isLocked(userId) ? "❌ 락 미해제" : "✅ 모든 락 해제");
    }

    @Test
    @DisplayName("부하 테스트 - 높은 동시성")
    void load_test_high_concurrency() throws InterruptedException {
        // given
        Long userId = 200L;
        userPointTable.insertOrUpdate(userId, 50000L);
        
        int threadCount = 50;
        int operationsPerThread = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        List<Long> responseTimes = new ArrayList<>();
        
        // when - 높은 부하 상황 시뮬레이션
        long testStartTime = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int op = 0; op < operationsPerThread; op++) {
                        long opStartTime = System.nanoTime();
                        
                        try {
                            // 무작위 작업 (충전 또는 사용)
                            if (Math.random() > 0.5) {
                                pointService.charge(userId, 100L);
                            } else {
                                pointService.use(userId, 50L);
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                        
                        long opEndTime = System.nanoTime();
                        long responseTimeMs = (opEndTime - opStartTime) / 1_000_000;
                        totalResponseTime.addAndGet(responseTimeMs);
                        
                        synchronized (responseTimes) {
                            responseTimes.add(responseTimeMs);
                        }
                        
                        // 실제 사용 패턴 시뮬레이션을 위한 작은 지연
                        try {
                            Thread.sleep((long) (Math.random() * 10));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await();
        long testEndTime = System.nanoTime();
        
        executor.shutdown();
        
        // then - 부하 테스트 결과 분석
        long totalTestTimeMs = (testEndTime - testStartTime) / 1_000_000;
        int totalOperations = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / totalOperations * 100;
        double avgResponseTime = (double) totalResponseTime.get() / totalOperations;
        double throughput = (totalOperations * 1000.0) / totalTestTimeMs;
        
        // 응답시간 분포 계산
        List<Long> sortedResponseTimes = responseTimes.stream().sorted().toList();
        long p50ResponseTime = sortedResponseTimes.get(sortedResponseTimes.size() / 2);
        long p95ResponseTime = sortedResponseTimes.get((int) (sortedResponseTimes.size() * 0.95));
        long p99ResponseTime = sortedResponseTimes.get((int) (sortedResponseTimes.size() * 0.99));
        long maxResponseTime = sortedResponseTimes.get(sortedResponseTimes.size() - 1);
        
        System.out.println("=== 부하 테스트 결과 ===");
        System.out.printf("테스트 시간: %d ms%n", totalTestTimeMs);
        System.out.printf("총 작업 수: %d (성공: %d, 실패: %d)%n", totalOperations, successCount.get(), failureCount.get());
        System.out.printf("성공률: %.1f%%%n", successRate);
        System.out.printf("전체 처리량: %.0f TPS%n", throughput);
        System.out.printf("평균 응답시간: %.2f ms%n", avgResponseTime);
        System.out.printf("응답시간 중간값(P50): %d ms%n", p50ResponseTime);
        System.out.printf("응답시간 95%%: %d ms%n", p95ResponseTime);
        System.out.printf("응답시간 99%%: %d ms%n", p99ResponseTime);
        System.out.printf("최대 응답시간: %d ms%n", maxResponseTime);
        
        // 최종 상태 검증
        UserPoint finalPoint = userPointTable.selectById(userId);
        System.out.printf("최종 포인트: %d (음수가 아님: %s)%n", 
                finalPoint.point(), finalPoint.point() >= 0 ? "✅" : "❌");
        System.out.printf("락 해제 상태: %s%n", lockManager.isLocked(userId) ? "❌ 락 미해제" : "✅ 정상");
        System.out.printf("활성 락 개수: %d%n", lockManager.getActiveLocksCount());
    }

    @Test
    @DisplayName("메모리 사용량 측정")
    void memory_usage_test() throws InterruptedException {
        // given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int userCount = 100;
        int operationsPerUser = 10;
        
        // 많은 수의 유저로 락 객체 생성 테스트
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        
        // when - 다수의 유저별 락 생성
        for (int userId = 1; userId <= userCount; userId++) {
            userPointTable.insertOrUpdate((long) userId, 1000L);
            
            final Long currentUserId = (long) userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int op = 0; op < operationsPerUser; op++) {
                        pointService.charge(currentUserId, 50L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();
        
        // GC 실행 후 메모리 측정
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;
        
        // then - 메모리 사용량 분석
        System.out.println("=== 메모리 사용량 측정 결과 ===");
        System.out.printf("초기 메모리: %.2f MB%n", initialMemory / 1024.0 / 1024.0);
        System.out.printf("최종 메모리: %.2f MB%n", finalMemory / 1024.0 / 1024.0);
        System.out.printf("사용된 메모리: %.2f MB%n", memoryUsed / 1024.0 / 1024.0);
        System.out.printf("생성된 락 개수: %d개%n", userCount);
        System.out.printf("락당 평균 메모리: %.2f KB%n", (memoryUsed / 1024.0) / userCount);
        System.out.printf("활성 락 개수: %d개%n", lockManager.getActiveLocksCount());
    }
}