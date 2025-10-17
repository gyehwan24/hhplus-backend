package kr.hhplus.be.server.payment.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;
import kr.hhplus.be.server.payment.domain.model.Payment;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment JPA 엔티티 (Infrastructure 전용 DTO)
 * - 순수 도메인 모델과 분리
 * - ORM 매핑 책임만 담당
 * - package-private (외부 노출 방지)
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
class PaymentEntity {

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

    /**
     * Domain → Entity 변환 (Infrastructure 책임)
     * @param domain 순수 도메인 객체
     * @return JPA 엔티티
     */
    public static PaymentEntity from(Payment domain) {
        PaymentEntity entity = new PaymentEntity();
        entity.reservationId = domain.getReservationId();
        entity.userId = domain.getUserId();
        entity.amount = domain.getAmount();
        entity.status = domain.getStatus();
        entity.paidAt = domain.getPaidAt();
        entity.failureReason = domain.getFailureReason();
        return entity;
    }

    /**
     * Entity → Domain 변환
     * @return 순수 도메인 객체
     */
    public Payment toDomain() {
        Payment payment;

        // 상태에 따라 적절한 팩토리 메서드 호출
        if (this.status == PaymentStatus.FAILED) {
            payment = Payment.fail(
                this.reservationId,
                this.userId,
                this.amount,
                this.failureReason
            );
        } else {
            // COMPLETED, PENDING
            payment = Payment.complete(
                this.reservationId,
                this.userId,
                this.amount
            );
        }

        // CANCELLED 상태 처리
        if (this.status == PaymentStatus.CANCELLED) {
            payment = payment.cancel();
        }

        // ID 할당
        payment.assignId(this.id);

        return payment;
    }
}
