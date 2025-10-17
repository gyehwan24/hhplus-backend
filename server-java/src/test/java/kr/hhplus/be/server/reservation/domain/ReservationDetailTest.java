package kr.hhplus.be.server.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationDetailTest {

    @Test
    @DisplayName("예약 상세 생성 성공")
    void create_성공() {
        // given
        Long reservationId = 1L;
        Long seatId = 100L;
        Integer seatNumber = 1;
        BigDecimal price = new BigDecimal("50000");

        // when
        ReservationDetail detail = ReservationDetail.create(
            reservationId, seatId, seatNumber, price
        );

        // then
        assertThat(detail.getReservationId()).isEqualTo(reservationId);
        assertThat(detail.getSeatId()).isEqualTo(seatId);
        assertThat(detail.getSeatNumber()).isEqualTo(seatNumber);
        assertThat(detail.getPrice()).isEqualTo(price);
    }
}
