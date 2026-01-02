package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentCompletedMessage;
import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentKafkaProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataPlatformEventListener 단위 테스트")
class DataPlatformEventListenerTest {

    @Mock
    private PaymentKafkaProducer kafkaProducer;

    @InjectMocks
    private DataPlatformEventListener listener;

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 Kafka로 메시지 발행")
    void onPaymentCompleted_성공() {
        // given
        PaymentCompletedEvent event = createEvent();

        // when
        listener.onPaymentCompleted(event);

        // then
        verify(kafkaProducer).send(any(PaymentCompletedMessage.class));
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 예외가 전파되지 않음")
    void onPaymentCompleted_발행실패_예외미전파() {
        // given
        PaymentCompletedEvent event = createEvent();
        doThrow(new RuntimeException("Kafka 오류"))
            .when(kafkaProducer).send(any());

        // when & then - 예외가 전파되지 않음
        assertDoesNotThrow(() -> listener.onPaymentCompleted(event));
    }

    @Test
    @DisplayName("좌석 정보가 없어도 정상 동작")
    void onPaymentCompleted_좌석정보없음_정상() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L,
            new BigDecimal("50000"),
            List.of()  // 빈 좌석 정보
        );

        // when
        listener.onPaymentCompleted(event);

        // then
        verify(kafkaProducer).send(any(PaymentCompletedMessage.class));
    }

    private PaymentCompletedEvent createEvent() {
        return PaymentCompletedEvent.of(
            1L,  // paymentId
            1L,  // reservationId
            1L,  // userId
            1L,  // concertId
            1L,  // scheduleId
            new BigDecimal("100000"),  // amount
            List.of(
                new PaymentCompletedEvent.SeatInfo(1L, 1, new BigDecimal("50000")),
                new PaymentCompletedEvent.SeatInfo(2L, 2, new BigDecimal("50000"))
            )
        );
    }
}
