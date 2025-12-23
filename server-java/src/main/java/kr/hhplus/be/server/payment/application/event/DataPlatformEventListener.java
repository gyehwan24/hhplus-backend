package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentCompletedMessage;
import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 데이터 플랫폼 이벤트 리스너
 * 결제 완료 이벤트를 수신하여 Kafka로 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final PaymentKafkaProducer kafkaProducer;

    /**
     * 결제 완료 이벤트 처리
     * 트랜잭션 커밋 후 Kafka로 메시지 발행
     *
     * @param event 결제 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            PaymentCompletedMessage message = toMessage(event);
            kafkaProducer.send(message);
            log.debug("데이터 플랫폼 Kafka 발행 완료 - reservationId: {}", event.reservationId());
        } catch (Exception e) {
            log.error("데이터 플랫폼 Kafka 발행 실패 - reservationId: {}", event.reservationId(), e);
        }
    }

    private PaymentCompletedMessage toMessage(PaymentCompletedEvent event) {
        List<PaymentCompletedMessage.SeatInfo> seatInfos = event.seats().stream()
                .map(seat -> new PaymentCompletedMessage.SeatInfo(
                        seat.seatId(),
                        seat.seatNumber(),
                        seat.price()))
                .toList();

        return new PaymentCompletedMessage(
                event.paymentId(),
                event.reservationId(),
                event.userId(),
                event.concertId(),
                event.scheduleId(),
                event.amount(),
                seatInfos,
                event.completedAt()
        );
    }
}
