package kr.hhplus.be.server.scheduler;

import kr.hhplus.be.server.token.application.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 활성 유저 정리 스케줄러
 *
 * Redis queue:active에서 만료 시각이 지난 유저를 주기적으로 정리합니다.
 * 분산락을 통해 다중 서버 환경에서 중복 실행을 방지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveUserCleanupScheduler {

    private final TokenService tokenService;

    /**
     * 10초마다 만료된 활성 유저 정리
     *
     * - TokenService.expireExpiredTokens()에 @DistributedLock이 적용되어 있음
     * - 다중 서버 환경에서 하나의 서버만 락을 획득하고 실행
     */
    @Scheduled(fixedDelay = 10_000) // 10초
    public void cleanupExpiredUsers() {
        try {
            int expiredCount = tokenService.expireExpiredTokens();

            if (expiredCount > 0) {
                log.info("만료된 활성 유저 정리 완료: {}명", expiredCount);
            }
        } catch (Exception e) {
            log.debug("활성 유저 정리 스케줄러 실행 실패 (락 획득 실패 또는 예외): {}", e.getMessage());
        }
    }
}
