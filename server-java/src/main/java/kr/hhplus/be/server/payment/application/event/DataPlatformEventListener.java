package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformClient;
import kr.hhplus.be.server.payment.infrastructure.external.ReservationDataPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 데이터 플랫폼 이벤트 리스너
 * 결제 완료 이벤트를 수신하여 데이터 플랫폼에 예약 정보 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final DataPlatformClient dataPlatformClient;

    /**
     * 결제 완료 이벤트 처리 - 데이터 플랫폼 전송
     *
     * @Async: 비동기로 실행하여 결제 응답 지연 방지
     * @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후에만 실행
     *
     * @param event 결제 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[DataPlatformListener] 이벤트 수신 - reservationId: {}", event.reservationId());

        try {
            ReservationDataPayload payload = ReservationDataPayload.from(event);
            dataPlatformClient.sendReservationData(payload);

            log.info("[DataPlatformListener] 전송 완료 - reservationId: {}", event.reservationId());

        } catch (Exception e) {
            // 데이터 플랫폼 전송 실패가 결제에 영향을 주면 안 됨
            log.error("[DataPlatformListener] 전송 실패 - reservationId: {}", event.reservationId(), e);
            // TODO: 재시도 큐 등록 또는 알림 발송
        }
    }
}
