package kr.hhplus.be.server.payment.domain;

import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment 도메인 테스트")
class PaymentTest {

    @Test
    @DisplayName("결제 생성 성공 - COMPLETED 상태로 생성")
    void create_성공_COMPLETED상태() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("50000");

        // when
        Payment payment = Payment.complete(reservationId, userId, amount);

        // then
        assertThat(payment.getReservationId()).isEqualTo(reservationId);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 생성 실패 - 금액이 0 이하")
    void create_실패_금액0이하() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal amount = BigDecimal.ZERO;

        // when & then
        assertThatThrownBy(() -> Payment.complete(reservationId, userId, amount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("결제 금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("결제 생성 실패 - 금액이 음수")
    void create_실패_금액음수() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("-1000");

        // when & then
        assertThatThrownBy(() -> Payment.complete(reservationId, userId, amount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("결제 금액은 양수여야 합니다");
    }

    @Test
    @DisplayName("결제 생성 실패 - reservationId가 null")
    void create_실패_reservationId_null() {
        // given
        Long reservationId = null;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("50000");

        // when & then
        assertThatThrownBy(() -> Payment.complete(reservationId, userId, amount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("예약 ID는 필수입니다");
    }

    @Test
    @DisplayName("결제 생성 실패 - userId가 null")
    void create_실패_userId_null() {
        // given
        Long reservationId = 1L;
        Long userId = null;
        BigDecimal amount = new BigDecimal("50000");

        // when & then
        assertThatThrownBy(() -> Payment.complete(reservationId, userId, amount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @Test
    @DisplayName("결제 취소 성공")
    void cancel_성공() {
        // given
        Payment payment = Payment.complete(1L, 1L, new BigDecimal("50000"));

        // when
        payment.cancel();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 실패 처리 성공")
    void fail_성공() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("50000");
        String failureReason = "잔액 부족";

        // when
        Payment payment = Payment.fail(reservationId, userId, amount, failureReason);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(failureReason);
        assertThat(payment.getAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("결제 완료 여부 확인 - COMPLETED 상태")
    void isCompleted_완료상태_true() {
        // given
        Payment payment = Payment.complete(1L, 1L, new BigDecimal("50000"));

        // when
        boolean result = payment.isCompleted();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("결제 완료 여부 확인 - FAILED 상태")
    void isCompleted_실패상태_false() {
        // given
        Payment payment = Payment.fail(1L, 1L, new BigDecimal("50000"), "잔액 부족");

        // when
        boolean result = payment.isCompleted();

        // then
        assertThat(result).isFalse();
    }
}
