package kr.hhplus.be.server.concert.domain;

import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleSeatTest {

    @Test
    @DisplayName("좌석 생성 시 기본 상태는 AVAILABLE")
    void create_기본상태_AVAILABLE() {
        // given & when
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .build();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("좌석 예약 가능 여부 체크")
    void isAvailable_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .build();

        // when
        boolean result = seat.isAvailable();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("RESERVED 상태일 때 예약 불가")
    void isAvailable_예약됨_false() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.RESERVED)
            .build();

        // when
        boolean result = seat.isAvailable();

        // then
        assertThat(result).isFalse();
    }
}
