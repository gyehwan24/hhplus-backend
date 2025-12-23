package kr.hhplus.be.server.payment.infrastructure.external;

import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReservationDataPayload 단위 테스트")
class ReservationDataPayloadTest {

    @Test
    @DisplayName("PaymentCompletedEvent로부터 페이로드 생성")
    void from_이벤트변환_성공() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 10L, 100L, 1000L, 10000L,
            new BigDecimal("150000"),
            List.of(
                new PaymentCompletedEvent.SeatInfo(1L, 1, new BigDecimal("50000")),
                new PaymentCompletedEvent.SeatInfo(2L, 2, new BigDecimal("50000")),
                new PaymentCompletedEvent.SeatInfo(3L, 3, new BigDecimal("50000"))
            )
        );

        // when
        ReservationDataPayload payload = ReservationDataPayload.from(event);

        // then
        assertThat(payload.reservationId()).isEqualTo(10L);
        assertThat(payload.userId()).isEqualTo(100L);
        assertThat(payload.concertId()).isEqualTo(1000L);
        assertThat(payload.scheduleId()).isEqualTo(10000L);
        assertThat(payload.totalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(payload.seats()).hasSize(3);
        assertThat(payload.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("좌석 정보가 올바르게 변환됨")
    void from_좌석정보변환_성공() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L,
            new BigDecimal("50000"),
            List.of(new PaymentCompletedEvent.SeatInfo(99L, 15, new BigDecimal("50000")))
        );

        // when
        ReservationDataPayload payload = ReservationDataPayload.from(event);

        // then
        ReservationDataPayload.SeatPayload seat = payload.seats().get(0);
        assertThat(seat.seatId()).isEqualTo(99L);
        assertThat(seat.seatNumber()).isEqualTo(15);
        assertThat(seat.price()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("빈 좌석 리스트도 정상 변환")
    void from_빈좌석리스트_성공() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L,
            BigDecimal.ZERO,
            List.of()
        );

        // when
        ReservationDataPayload payload = ReservationDataPayload.from(event);

        // then
        assertThat(payload.seats()).isEmpty();
    }
}
