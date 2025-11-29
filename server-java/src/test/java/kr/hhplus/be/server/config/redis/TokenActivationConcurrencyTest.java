package kr.hhplus.be.server.config.redis;

import kr.hhplus.be.server.token.application.TokenService;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 활성화 스케줄러 동시성 테스트
 * 다중 서버 환경을 시뮬레이션하여 분산락이 제대로 작동하는지 검증합니다.
 */
@SpringBootTest
@DisplayName("토큰 활성화 동시성 테스트 (다중 서버 시뮬레이션)")
class TokenActivationConcurrencyTest extends BaseRedisTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUpTest() {
        // Redis 락 키 삭제
        redisTemplate.delete("scheduler:token:activate");
    }

    @AfterEach
    void tearDownTest() {
        // Redis 락 키 삭제
        redisTemplate.delete("scheduler:token:activate");
    }

    @Test
    @DisplayName("3개 서버가 동시에 스케줄러 실행 시 하나만 토큰 활성화")
    void threeServers_onlyOneActivates() throws InterruptedException {
        // Given: 150개의 대기 토큰 생성 (MAX_ACTIVE_TOKENS = 100)
        for (long i = 1; i <= 150; i++) {
            Token token = Token.issue(i);
            tokenRepository.save(token);
        }

        // 모든 토큰이 WAITING 상태인지 확인
        long waitingCount = tokenRepository.countByStatus(TokenStatus.WAITING);
        assertThat(waitingCount).isEqualTo(150);

        // Given: 3개 서버 시뮬레이션
        int serverCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(serverCount);
        CountDownLatch latch = new CountDownLatch(serverCount);

        AtomicInteger successCount = new AtomicInteger(0); // 성공한 서버 수
        AtomicInteger totalActivated = new AtomicInteger(0); // 총 활성화된 토큰 수

        // When: 3개 서버가 동시에 activateWaitingTokens() 호출
        for (int i = 0; i < serverCount; i++) {
            final int serverId = i + 1;
            executorService.submit(() -> {
                try {
                    int activated = tokenService.activateWaitingTokens();
                    System.out.println("Server " + serverId + " activated: " + activated + " tokens");

                    if (activated > 0) {
                        successCount.incrementAndGet();
                        totalActivated.addAndGet(activated);
                    }
                } catch (LockAcquisitionException e) {
                    System.out.println("Server " + serverId + " failed to acquire lock: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Server " + serverId + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 하나의 서버만 성공
        assertThat(successCount.get()).isEqualTo(1);

        // Then: 정확히 100개만 활성화 (MAX_ACTIVE_TOKENS)
        assertThat(totalActivated.get()).isEqualTo(100);

        // Then: DB 상태 확인
        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        long remainingWaitingCount = tokenRepository.countByStatus(TokenStatus.WAITING);

        assertThat(activeCount).isEqualTo(100);
        assertThat(remainingWaitingCount).isEqualTo(50); // 150 - 100 = 50
    }

    @Test
    @DisplayName("5개 서버가 동시에 스케줄러 실행 시 중복 활성화 방지")
    void fiveServers_preventDuplicateActivation() throws InterruptedException {
        // Given: 200개의 대기 토큰 생성
        for (long i = 1; i <= 200; i++) {
            Token token = Token.issue(i);
            tokenRepository.save(token);
        }

        // Given: 5개 서버 시뮬레이션
        int serverCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(serverCount);
        CountDownLatch latch = new CountDownLatch(serverCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개 서버가 정확히 동시에 실행되도록 CountDownLatch 사용
        CountDownLatch readyLatch = new CountDownLatch(serverCount);

        for (int i = 0; i < serverCount; i++) {
            final int serverId = i + 1;
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    readyLatch.countDown();
                    readyLatch.await();

                    int activated = tokenService.activateWaitingTokens();
                    if (activated > 0) {
                        successCount.incrementAndGet();
                        System.out.println("Server " + serverId + " succeeded: " + activated + " tokens");
                    } else {
                        System.out.println("Server " + serverId + " skipped (no tokens or lock failed)");
                    }
                } catch (Exception e) {
                    System.out.println("Server " + serverId + " failed: " + e.getClass().getSimpleName());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 최대 1개 서버만 성공 (분산락으로 보장)
        assertThat(successCount.get()).isLessThanOrEqualTo(1);

        // Then: 활성 토큰은 정확히 100개
        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        assertThat(activeCount).isEqualTo(100);
    }

    @Test
    @DisplayName("스케줄러 순차 실행 시 모든 토큰 활성화")
    void sequentialExecution_activatesAllTokens() {
        // Given: 250개의 대기 토큰 생성
        for (long i = 1; i <= 250; i++) {
            Token token = Token.issue(i);
            tokenRepository.save(token);
        }

        // When: 스케줄러를 3번 순차 실행 (실제 환경에서는 10초 간격)
        int firstRun = tokenService.activateWaitingTokens();
        int secondRun = tokenService.activateWaitingTokens();
        int thirdRun = tokenService.activateWaitingTokens();

        System.out.println("1st run: " + firstRun);
        System.out.println("2nd run: " + secondRun);
        System.out.println("3rd run: " + thirdRun);

        // Then: 1차 100개, 2차 100개, 3차 50개 활성화
        assertThat(firstRun).isEqualTo(100);
        assertThat(secondRun).isEqualTo(100);
        assertThat(thirdRun).isEqualTo(50);

        // Then: 모든 토큰이 활성화됨
        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        assertThat(activeCount).isEqualTo(250);
    }

    @Test
    @DisplayName("활성 토큰이 이미 100개일 때 스케줄러 실행 시 활성화 안 함")
    void whenMaxActiveTokens_doNotActivateMore() {
        // Given: 100개 ACTIVE, 50개 WAITING
        for (long i = 1; i <= 100; i++) {
            Token token = Token.issue(i);
            Token activated = token.activate();
            tokenRepository.save(activated);
        }

        for (long i = 101; i <= 150; i++) {
            Token token = Token.issue(i);
            tokenRepository.save(token);
        }

        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        long waitingCount = tokenRepository.countByStatus(TokenStatus.WAITING);
        assertThat(activeCount).isEqualTo(100);
        assertThat(waitingCount).isEqualTo(50);

        // When: 스케줄러 실행
        int activated = tokenService.activateWaitingTokens();

        // Then: 활성화 안 됨
        assertThat(activated).isEqualTo(0);

        // Then: 상태 유지
        activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        waitingCount = tokenRepository.countByStatus(TokenStatus.WAITING);
        assertThat(activeCount).isEqualTo(100);
        assertThat(waitingCount).isEqualTo(50);
    }

    @Test
    @DisplayName("분산락 타임아웃 테스트 - 락 보유 중 다른 서버 접근")
    void lockTimeout_whenAnotherServerHoldsLock() throws InterruptedException {
        // Given: 100개 대기 토큰
        for (long i = 1; i <= 100; i++) {
            Token token = Token.issue(i);
            tokenRepository.save(token);
        }

        // Given: 서버 A가 락을 획득하고 5초간 보유 (의도적으로 긴 작업)
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicInteger serverAResult = new AtomicInteger(-1);
        AtomicInteger serverBResult = new AtomicInteger(-1);

        // 서버 A: 락 획득 후 긴 작업
        executorService.submit(() -> {
            try {
                serverAResult.set(tokenService.activateWaitingTokens());
                Thread.sleep(100); // 작업 시뮬레이션
            } catch (Exception e) {
                System.out.println("Server A error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 50ms 후 서버 B 시작 (서버 A가 락 보유 중)
        Thread.sleep(50);

        // 서버 B: 락 획득 시도 (waitTime=3초)
        executorService.submit(() -> {
            try {
                serverBResult.set(tokenService.activateWaitingTokens());
            } catch (LockAcquisitionException e) {
                System.out.println("Server B failed to acquire lock (expected)");
                serverBResult.set(0);
            } catch (Exception e) {
                System.out.println("Server B error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executorService.shutdown();

        // Then: 서버 A만 성공
        assertThat(serverAResult.get()).isEqualTo(100);
        assertThat(serverBResult.get()).isEqualTo(0);

        // Then: 활성 토큰 100개
        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        assertThat(activeCount).isEqualTo(100);
    }
}
