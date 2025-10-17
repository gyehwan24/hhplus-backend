package kr.hhplus.be.server.payment.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 엔티티
 * 예약에 대한 결제 정보를 관리
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reservationId;

    private Long userId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime paidAt;

    private String failureReason;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private Payment(Long reservationId, Long userId, BigDecimal amount,
                   PaymentStatus status, LocalDateTime paidAt, String failureReason) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.failureReason = failureReason;
    }

    /**
     * 결제 완료 생성
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @return 결제 완료 객체
     */
    public static Payment complete(Long reservationId, Long userId, BigDecimal amount) {
        validatePaymentCreation(reservationId, userId, amount);

        return Payment.builder()
            .reservationId(reservationId)
            .userId(userId)
            .amount(amount)
            .status(PaymentStatus.COMPLETED)
            .paidAt(LocalDateTime.now())
            .build();
    }

    /**
     * 결제 실패 생성
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param failureReason 실패 사유
     * @return 결제 실패 객체
     */
    public static Payment fail(Long reservationId, Long userId, BigDecimal amount, String failureReason) {
        validatePaymentCreation(reservationId, userId, amount);

        return Payment.builder()
            .reservationId(reservationId)
            .userId(userId)
            .amount(amount)
            .status(PaymentStatus.FAILED)
            .failureReason(failureReason)
            .build();
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    private static void validatePaymentCreation(Long reservationId, Long userId, BigDecimal amount) {
        if (reservationId == null) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }
    }
}
