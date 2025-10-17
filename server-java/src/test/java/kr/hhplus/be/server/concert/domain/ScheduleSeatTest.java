package kr.hhplus.be.server.concert.domain;

import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("좌석 예약 성공 - AVAILABLE에서 RESERVED로 변경")
    void reserve_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.AVAILABLE)
            .build();

        // when
        seat.reserve();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(seat.getReservedUntil()).isNotNull();
    }

    @Test
    @DisplayName("이미 예약된 좌석은 예약 불가 - 예외 발생")
    void reserve_이미예약됨_예외() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.RESERVED)
            .build();

        // when & then
        assertThatThrownBy(() -> seat.reserve())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("판매 완료된 좌석은 예약 불가 - 예외 발생")
    void reserve_판매완료_예외() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.SOLD)
            .build();

        // when & then
        assertThatThrownBy(() -> seat.reserve())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("좌석 확정 성공 - RESERVED에서 SOLD로 변경")
    void confirm_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.RESERVED)
            .build();

        // when
        seat.confirm();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    @DisplayName("예약되지 않은 좌석은 확정 불가 - 예외 발생")
    void confirm_예약안됨_예외() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.AVAILABLE)
            .build();

        // when & then
        assertThatThrownBy(() -> seat.confirm())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("좌석 예약 해제 성공 - RESERVED에서 AVAILABLE로 변경")
    void release_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.RESERVED)
            .build();

        // when
        seat.release();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getReservedUntil()).isNull();
    }

    @Test
    @DisplayName("예약되지 않은 좌석은 해제 불가 - 예외 발생")
    void release_예약안됨_예외() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .status(SeatStatus.AVAILABLE)
            .build();

        // when & then
        assertThatThrownBy(() -> seat.release())
            .isInstanceOf(IllegalStateException.class);
    }
}
