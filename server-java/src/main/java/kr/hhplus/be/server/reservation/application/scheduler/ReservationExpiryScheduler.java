package kr.hhplus.be.server.reservation.application.scheduler;

import kr.hhplus.be.server.reservation.application.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 만료 스케줄러
 * - 1분마다 만료된 예약을 자동으로 취소하고 좌석 해제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationService reservationService;

    /**
     * 만료된 예약 처리 스케줄러
     * - 실행 주기: 1분마다 (fixedRate)
     * - 초기 지연: 1분 (initialDelay)
     */
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void expireReservations() {
        try {
            log.debug("예약 만료 스케줄러 시작");

            int expiredCount = reservationService.expireReservationsAndReleaseSeats();

            if (expiredCount > 0) {
                log.info("예약 만료 스케줄러 완료 - 처리된 예약: {}건", expiredCount);
            } else {
                log.debug("예약 만료 스케줄러 완료 - 처리할 예약 없음");
            }

        } catch (Exception e) {
            // 스케줄러는 예외가 발생해도 중단되지 않아야 함
            log.error("예약 만료 스케줄러 실행 중 오류 발생", e);
        }
    }
}
