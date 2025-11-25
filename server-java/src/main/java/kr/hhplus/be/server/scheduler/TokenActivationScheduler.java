package kr.hhplus.be.server.scheduler;

import kr.hhplus.be.server.token.application.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토큰 활성화 스케줄러
 *
 * 대기 중인 토큰을 주기적으로 활성화합니다.
 * 분산락을 통해 다중 서버 환경에서 중복 실행을 방지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenActivationScheduler {

    private final TokenService tokenService;

    /**
     * 10초마다 대기 중인 토큰 활성화
     *
     * - TokenService.activateWaitingTokens()에 @DistributedLock이 적용되어 있음
     * - 다중 서버 환경에서 하나의 서버만 락을 획득하고 실행
     * - 나머지 서버는 락 획득 실패로 스킵
     */
    @Scheduled(fixedDelay = 10_000) // 10초
    public void activateTokens() {
        try {
            int activatedCount = tokenService.activateWaitingTokens();

            if (activatedCount > 0) {
                log.info("토큰 활성화 완료: {}개", activatedCount);
            }
        } catch (Exception e) {
            // 락 획득 실패 시 로그만 남기고 계속 진행
            log.debug("토큰 활성화 스케줄러 실행 실패 (락 획득 실패 또는 예외): {}", e.getMessage());
        }
    }
}
