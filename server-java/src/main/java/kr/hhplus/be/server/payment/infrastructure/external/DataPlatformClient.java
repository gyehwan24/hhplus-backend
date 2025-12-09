package kr.hhplus.be.server.payment.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 데이터 플랫폼 클라이언트 (Mock)
 * 실제 환경에서는 RestTemplate, WebClient 등으로 외부 API 호출
 */
@Slf4j
@Component
public class DataPlatformClient {

    /**
     * 예약 데이터를 데이터 플랫폼에 전송 (Mock)
     *
     * @param payload 전송할 예약 데이터
     */
    public void sendReservationData(ReservationDataPayload payload) {
        log.info("[DataPlatform] 예약 데이터 전송 시작 - reservationId: {}, concertId: {}, userId: {}",
            payload.reservationId(), payload.concertId(), payload.userId());

        // Mock: 실제 API 호출 시뮬레이션
        try {
            // 외부 API 호출 지연 시뮬레이션 (100ms)
            Thread.sleep(100);

            log.info("[DataPlatform] 예약 데이터 전송 완료 - reservationId: {}, 좌석 수: {}, 총액: {}",
                payload.reservationId(), payload.seats().size(), payload.totalAmount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("데이터 플랫폼 전송 중 인터럽트 발생", e);
        }
    }
}
