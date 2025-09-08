package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 간단한 성능 측정 테스트
 */
@SpringBootTest
@DisplayName("간단한 성능 측정 테스트")
class SimplePerformanceTest {

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
    @DisplayName("단일 유저 순차 처리 성능")
    void single_user_sequential_performance() {
        // given
        Long userId = 1L;
        userPointTable.insertOrUpdate(userId, 10000L);
        int operationCount = 100;
        
        // when
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < operationCount; i++) {
            pointService.charge(userId, 100L);
        }
        
        long endTime = System.currentTimeMillis();
        
        // then
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / operationCount;
        double tps = 1000.0 / avgTime;
        
        System.out.println("=== 단일 유저 순차 처리 성능 ===");
        System.out.printf("총 실행시간: %d ms%n", totalTime);
        System.out.printf("평균 처리시간: %.2f ms/작업%n", avgTime);
        System.out.printf("처리량(TPS): %.0f%n", tps);
        
        // 정확성 검증
        UserPoint finalPoint = userPointTable.selectById(userId);
        System.out.printf("포인트 정확성: %s (기대: %d, 실제: %d)%n", 
                finalPoint.point() == 20000L ? "✅" : "❌", 20000L, finalPoint.point());
    }

    @Test
    @DisplayName("다중 유저 동시 처리 성능")
    void multi_user_concurrent_performance() throws InterruptedException {
        // given
        int userCount = 5;
        int operationsPerUser = 10;
        
        for (int i = 1; i <= userCount; i++) {
            userPointTable.insertOrUpdate((long) i, 5000L);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        
        // when
        long overallStartTime = System.currentTimeMillis();
        
        for (int userId = 1; userId <= userCount; userId++) {
            final Long currentUserId = (long) userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int op = 0; op < operationsPerUser; op++) {
                        pointService.charge(currentUserId, 100L);
                        completedOperations.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long overallEndTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        // then
        long overallTime = overallEndTime - overallStartTime;
        int totalOperations = userCount * operationsPerUser;
        double overallTps = (totalOperations * 1000.0) / overallTime;
        
        System.out.println("=== 다중 유저 동시 처리 성능 ===");
        System.out.printf("전체 실행시간: %d ms%n", overallTime);
        System.out.printf("총 작업 수: %d%n", totalOperations);
        System.out.printf("완료된 작업: %d%n", completedOperations.get());
        System.out.printf("전체 처리량(TPS): %.0f%n", overallTps);
        
        // 각 유저별 정확성 검증
        for (int userId = 1; userId <= userCount; userId++) {
            UserPoint userPoint = userPointTable.selectById((long) userId);
            long expected = 5000L + (100L * operationsPerUser);
            System.out.printf("User %d: %s (기대: %d, 실제: %d)%n", 
                    userId, userPoint.point() == expected ? "✅" : "❌", expected, userPoint.point());
        }
    }

    @Test
    @DisplayName("동일 유저 경합 상황 성능")
    void same_user_contention_performance() throws InterruptedException {
        // given
        Long userId = 100L;
        userPointTable.insertOrUpdate(userId, 10000L);
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        
        // when
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.charge(userId, 100L);
                    completedOperations.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        // then
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / completedOperations.get();
        
        System.out.println("=== 동일 유저 경합 상황 성능 ===");
        System.out.printf("총 실행시간: %d ms%n", totalTime);
        System.out.printf("완료된 작업: %d개%n", completedOperations.get());
        System.out.printf("평균 처리시간: %.2f ms/작업%n", avgTime);
        
        // 순차 처리 확인
        UserPoint finalPoint = userPointTable.selectById(userId);
        long expected = 10000L + (100L * completedOperations.get());
        System.out.printf("순차 처리 검증: %s (기대: %d, 실제: %d)%n", 
                finalPoint.point() == expected ? "✅" : "❌", expected, finalPoint.point());
        
        System.out.printf("락 해제 상태: %s%n", lockManager.isLocked(userId) ? "❌" : "✅");
    }

    @Test
    @DisplayName("메모리 사용량 간단 측정")
    void simple_memory_usage_test() throws InterruptedException {
        // given
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        Thread.sleep(100);
        
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int userCount = 50;
        
        // when - 다수의 유저별 락 생성
        for (int userId = 1; userId <= userCount; userId++) {
            userPointTable.insertOrUpdate((long) userId, 1000L);
            pointService.charge((long) userId, 100L);
        }
        
        System.gc();
        Thread.sleep(100);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;
        
        // then
        System.out.println("=== 메모리 사용량 측정 ===");
        System.out.printf("초기 메모리: %.2f MB%n", initialMemory / 1024.0 / 1024.0);
        System.out.printf("최종 메모리: %.2f MB%n", finalMemory / 1024.0 / 1024.0);
        System.out.printf("사용된 메모리: %.2f MB%n", memoryUsed / 1024.0 / 1024.0);
        System.out.printf("생성된 락 개수: %d개%n", userCount);
        if (userCount > 0) {
            System.out.printf("락당 평균 메모리: %.2f KB%n", (memoryUsed / 1024.0) / userCount);
        }
    }

    @Test
    @DisplayName("읽기 vs 쓰기 성능 비교")
    void read_vs_write_performance() {
        // given
        Long userId = 200L;
        userPointTable.insertOrUpdate(userId, 5000L);
        
        int operationCount = 50;
        
        // 읽기 성능 측정
        long readStartTime = System.currentTimeMillis();
        for (int i = 0; i < operationCount; i++) {
            pointService.getPoint(userId);
        }
        long readEndTime = System.currentTimeMillis();
        
        // 쓰기 성능 측정
        long writeStartTime = System.currentTimeMillis();
        for (int i = 0; i < operationCount; i++) {
            pointService.charge(userId, 10L);
        }
        long writeEndTime = System.currentTimeMillis();
        
        // then
        long readTime = readEndTime - readStartTime;
        long writeTime = writeEndTime - writeStartTime;
        double readAvg = (double) readTime / operationCount;
        double writeAvg = (double) writeTime / operationCount;
        
        System.out.println("=== 읽기 vs 쓰기 성능 비교 ===");
        System.out.printf("읽기 총 시간: %d ms, 평균: %.2f ms/작업%n", readTime, readAvg);
        System.out.printf("쓰기 총 시간: %d ms, 평균: %.2f ms/작업%n", writeTime, writeAvg);
        System.out.printf("성능 비율: 쓰기가 읽기보다 %.1fx 느림%n", writeAvg / readAvg);
    }
}