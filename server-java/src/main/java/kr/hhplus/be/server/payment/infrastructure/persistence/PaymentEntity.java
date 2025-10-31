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
     * Reconstitute Pattern: 저장된 데이터를 도메인 객체로 재구성
     * @return 순수 도메인 객체
     */
    public Payment toDomain() {
        return Payment.reconstitute(
            this.id,
            this.reservationId,
            this.userId,
            this.amount,
            this.status,
            this.paidAt,
            this.failureReason
        );
    }
}
