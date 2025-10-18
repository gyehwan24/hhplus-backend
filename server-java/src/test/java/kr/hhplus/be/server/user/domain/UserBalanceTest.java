package kr.hhplus.be.server.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserBalanceTest {

    @Test
    @DisplayName("잔액 충전 성공")
    void charge_정상금액_성공() {
        // given
        Long userId = 1L;
        UserBalance userBalance = UserBalance.create(userId, new BigDecimal("1000"));
        // when
        UserBalance result = userBalance.charge(new BigDecimal("1000"));

        // then
        assertThat(result.getCurrentBalance()).isEqualTo(new BigDecimal("2000"));
    }

    @Test
    @DisplayName("음수 충전 시 예외 발생")
    void charge_음수금액_예외() {
        // given
        Long userId = 1L;
        UserBalance userBalance = UserBalance.create(userId, new BigDecimal("1000"));

        // when & then
        assertThatThrownBy(() -> userBalance.charge(new BigDecimal("-2000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("충전 금액은 양수여야 합니다.");
    }

    @Test
    @DisplayName("0원 충전 시 예외")
    void charge_0원_예외() {
        // given
        UserBalance userBalance = UserBalance.create(1L, new BigDecimal("1000"));

        // when & then
        assertThatThrownBy(() -> userBalance.charge(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔액 사용 성공")
    void use_정상금액_성공() {
        // given
        UserBalance userBalance = UserBalance.create(1L, new BigDecimal("10000"));

        // when
        UserBalance result = userBalance.use(new BigDecimal("500"));

        // then
        assertThat(result.getCurrentBalance()).isEqualTo(new BigDecimal("9500"));
    }

    @Test
    @DisplayName("잔액 부족 시 예외")
    void use_잔액부족_예외() {
        // given
        UserBalance userBalance = UserBalance.create(1L, new BigDecimal("1000"));

        // when & then
        assertThatThrownBy(() -> userBalance.use(new BigDecimal("2000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("잔액이 부족");
    }

    @Test
    @DisplayName("음수 사용 시 예외")
    void use_음수금액_예외() {
        // given
        UserBalance userBalance = UserBalance.create(1L, new BigDecimal("1000"));
        
        // when & then
        assertThatThrownBy(() -> userBalance.use(new BigDecimal("-100")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용 금액은 양수");
    }

    @Test
    @DisplayName("0원 사용 시 예외")
    void use_0원_예외() {
        // given
        UserBalance userBalance = UserBalance.create(1L, new BigDecimal("1000"));

        // when & then
        assertThatThrownBy(() -> userBalance.use(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용 금액은 양수");

    }
}
