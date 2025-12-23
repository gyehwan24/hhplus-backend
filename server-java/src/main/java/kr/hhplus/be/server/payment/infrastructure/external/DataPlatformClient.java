package kr.hhplus.be.server.payment.infrastructure.external;

import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentCompletedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 데이터 플랫폼 외부 API 클라이언트 (Mock)
 * 실제 환경에서는 RestTemplate 또는 WebClient로 외부 API 호출
 */
@Slf4j
@Component
public class DataPlatformClient {

    /**
     * 예약 데이터를 데이터 플랫폼으로 전송
     *
     * @param message 결제 완료 메시지
     */
    public void sendReservationData(PaymentCompletedMessage message) {
        log.info("[DataPlatformClient] 데이터 플랫폼으로 예약 정보 전송 시작 - reservationId: {}",
                message.reservationId());

        // Mock: 외부 API 호출 시뮬레이션 (100ms 지연)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[DataPlatformClient] 데이터 플랫폼 전송 완료 - paymentId: {}, userId: {}, concertId: {}, amount: {}",
                message.paymentId(),
                message.userId(),
                message.concertId(),
                message.amount());
    }
}
