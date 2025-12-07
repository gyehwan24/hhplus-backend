package kr.hhplus.be.server.payment.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentCompletedEvent 단위 테스트")
class PaymentCompletedEventTest {

    @Test
    @DisplayName("이벤트 생성 시 모든 필드가 올바르게 설정됨")
    void of_이벤트생성_성공() {
        // given
        Long paymentId = 1L;
        Long reservationId = 2L;
        Long userId = 3L;
        Long concertId = 4L;
        Long scheduleId = 5L;
        BigDecimal amount = new BigDecimal("100000");
        List<PaymentCompletedEvent.SeatInfo> seats = List.of(
            new PaymentCompletedEvent.SeatInfo(10L, 1, new BigDecimal("50000")),
            new PaymentCompletedEvent.SeatInfo(11L, 2, new BigDecimal("50000"))
        );

        // when
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            paymentId, reservationId, userId, concertId, scheduleId, amount, seats
        );

        // then
        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.reservationId()).isEqualTo(reservationId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.concertId()).isEqualTo(concertId);
        assertThat(event.scheduleId()).isEqualTo(scheduleId);
        assertThat(event.amount()).isEqualByComparingTo(amount);
        assertThat(event.seats()).hasSize(2);
        assertThat(event.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("SeatInfo가 올바르게 생성됨")
    void seatInfo_생성_성공() {
        // given & when
        PaymentCompletedEvent.SeatInfo seatInfo = new PaymentCompletedEvent.SeatInfo(
            1L, 15, new BigDecimal("75000")
        );

        // then
        assertThat(seatInfo.seatId()).isEqualTo(1L);
        assertThat(seatInfo.seatNumber()).isEqualTo(15);
        assertThat(seatInfo.price()).isEqualByComparingTo(new BigDecimal("75000"));
    }

    @Test
    @DisplayName("completedAt이 자동으로 현재 시간으로 설정됨")
    void of_completedAt_자동설정() {
        // when
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L, BigDecimal.TEN, List.of()
        );

        // then
        assertThat(event.completedAt()).isNotNull();
    }
}
