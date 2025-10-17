package kr.hhplus.be.server.reservation.domain;

import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    // ============================================
    // create() 테스트
    // ============================================

    @Test
    @DisplayName("예약 생성 성공 - 기본 상태는 PENDING")
    void create_성공() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        BigDecimal totalAmount = new BigDecimal("100000");

        // when
        Reservation reservation = Reservation.create(userId, scheduleId, totalAmount);

        // then
        assertThat(reservation.getUserId()).isEqualTo(userId);
        assertThat(reservation.getScheduleId()).isEqualTo(scheduleId);
        assertThat(reservation.getTotalAmount()).isEqualTo(totalAmount);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getExpiresAt()).isNotNull();
        assertThat(reservation.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("예약 생성 시 만료 시간은 10분 후로 설정")
    void create_만료시간_10분() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        BigDecimal totalAmount = new BigDecimal("100000");

        // when
        Reservation reservation = Reservation.create(userId, scheduleId, totalAmount);

        // then
        assertThat(reservation.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(reservation.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(11));
    }

    @Test
    @DisplayName("예약 생성 시 금액이 0 이하면 예외 발생")
    void create_금액0이하_예외() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        BigDecimal invalidAmount = BigDecimal.ZERO;

        // when & then
        assertThatThrownBy(() -> Reservation.create(userId, scheduleId, invalidAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("금액");
    }

    // ============================================
    // confirm() 테스트
    // ============================================

    @Test
    @DisplayName("예약 확정 성공 - PENDING에서 CONFIRMED로 변경")
    void confirm_성공() {
        // given
        Reservation reservation = Reservation.create(1L, 1L, new BigDecimal("10000"));

        // when
        reservation.confirm();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("이미 확정된 예약은 확정 불가 -1L, 1 예외 발생")
    void confirm_이미확정됨_예외() {
        // given
        Reservation reservation = Reservation.builder()
            .userId(1L)
            .scheduleId(1L)
            .totalAmount(new BigDecimal("100000"))
            .status(ReservationStatus.CONFIRMED)
            .build();

        // when & then
        assertThatThrownBy(() -> reservation.confirm())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("취소된 예약은 확정 불가 - 예외 발생")
    void confirm_취소됨_예외() {
        // given
        Reservation reservation = Reservation.builder()
            .userId(1L)
            .scheduleId(1L)
            .totalAmount(new BigDecimal("100000"))
            .status(ReservationStatus.CANCELLED)
            .build();

        // when & then
        assertThatThrownBy(() -> reservation.confirm())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료된 예약은 확정 불가 - 예외 발생")
    void confirm_만료됨_예외() {
        // given
        Reservation reservation = Reservation.builder()
            .userId(1L)
            .scheduleId(1L)
            .totalAmount(new BigDecimal("100000"))
            .status(ReservationStatus.EXPIRED)
            .build();

        // when & then
        assertThatThrownBy(() -> reservation.confirm())
            .isInstanceOf(IllegalStateException.class);
    }

    // ============================================
    // cancel() 테스트
    // ============================================

    @Test
    @DisplayName("예약 취소 성공 )")
    void cancel_성공() {
        // given
        Reservation reservation = Reservation.create(1L, 1L, new BigDecimal("10000"));

        // when
        reservation.cancel();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    // ============================================
    // expire() 테스트
    // ============================================

    @Test
    @DisplayName("예약 만료 성공 - PENDING에서 EXPIRED로 변경")
    void expire_성공() {
        // given
        Reservation reservation = Reservation.create(1L, 1L, new BigDecimal("10000"));

        // when
        reservation.expire();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 확정된 예약은 만료 불가 - 예외 발생")
    void expire_이미확정됨_예외() {
        // given
        Reservation reservation = Reservation.builder()
            .userId(1L)
            .scheduleId(1L)
            .totalAmount(new BigDecimal("100000"))
            .status(ReservationStatus.CONFIRMED)
            .build();

        // when & then
        assertThatThrownBy(() -> reservation.expire())
            .isInstanceOf(IllegalStateException.class);
    }

    // ============================================
    // isExpired() 테스트
    // ============================================

    @Test
    @DisplayName("만료 시간이 지나지 않았으면 false 반환")
    void isExpired_만료안됨_false() {
        // given
        Reservation reservation = Reservation.create(1L, 1L, new BigDecimal("10000"));

        // when
        boolean result = reservation.isExpired();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("만료 시간이 지났으면 true 반환")
    void isExpired_만료됨_true() {
        // given
        Reservation reservation = Reservation.builder()
            .userId(1L)
            .scheduleId(1L)
            .totalAmount(new BigDecimal("100000"))
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().minusMinutes(1))  // 1분 전으로 설정
            .build();

        // when
        boolean result = reservation.isExpired();

        // then
        assertThat(result).isTrue();
    }
}
