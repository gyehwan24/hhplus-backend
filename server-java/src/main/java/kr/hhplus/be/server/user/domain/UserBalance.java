package kr.hhplus.be.server.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private BigDecimal currentBalance;

    private BigDecimal totalCharged;

    private BigDecimal totalUsed;

    @Version
    private Integer version; // Optimistic Lock

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public UserBalance(Long userId, BigDecimal currentBalance) {
        this.userId = userId;
        this.currentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        this.totalCharged = BigDecimal.ZERO;
        this.totalUsed = BigDecimal.ZERO;
    }

    // 비즈니스 로직: 충전
    public UserBalance charge(BigDecimal amount) {
        validateChargeAmount(amount);

        BigDecimal newBalance = this.currentBalance.add(amount);

        this.currentBalance = newBalance;
        this.totalCharged = this.totalCharged.add(amount);

        return this;
    }

    // 비즈니스 로직: 사용
    public UserBalance use(BigDecimal amount) {
        validateUseAmount(amount);

        BigDecimal newBalance = this.currentBalance.subtract(amount);
        validateMinBalance(newBalance);

        this.currentBalance = newBalance;
        this.totalUsed = this.totalUsed.add(amount);
        return this;
    }

    private void validateChargeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 양수여야 합니다.");
        }
    }

    private void validateUseAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 금액은 양수여야 합니다.");
        }
    }

    private void validateMinBalance(BigDecimal newBalance) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
    }

    // 정적 팩토리 메서드
    public static UserBalance create(Long userId, BigDecimal amount) {
        return UserBalance.builder()
                .userId(userId)
                .currentBalance(amount)
                .build();
    }
}
