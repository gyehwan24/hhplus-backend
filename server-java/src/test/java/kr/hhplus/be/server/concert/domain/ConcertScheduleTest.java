package kr.hhplus.be.server.concert.domain;

import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertScheduleTest {

    @Test
    @DisplayName("예약 가능 기간인지 확인")
    void isBookingOpen_예약가능기간_true() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(1))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("예약 오픈 전")
    void isBookingOpen_오픈전_false() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().plusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(2))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("예약 마감 후")
    void isBookingOpen_마감후_false() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().minusDays(2))
            .bookingCloseAt(LocalDateTime.now().minusHours(1))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("SOLD_OUT 상태일 때 예약 불가")
    void isBookingOpen_매진상태_false() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(1))
            .status(ScheduleStatus.SOLD_OUT)
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isFalse();
    }
}
