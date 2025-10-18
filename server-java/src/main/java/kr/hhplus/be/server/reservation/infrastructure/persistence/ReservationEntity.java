package kr.hhplus.be.server.reservation.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 JPA 엔티티
 * Domain ↔ Entity 변환 담당 (Infrastructure)
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Domain → Entity 변환
     */
    public static ReservationEntity from(Reservation domain) {
        ReservationEntity entity = new ReservationEntity();
        entity.userId = domain.getUserId();
        entity.scheduleId = domain.getScheduleId();
        entity.totalAmount = domain.getTotalAmount();
        entity.status = domain.getStatus();
        entity.expiresAt = domain.getExpiresAt();
        return entity;
    }

    /**
     * Entity → Domain 변환
     */
    public Reservation toDomain() {
        Reservation reservation = Reservation.create(
            this.userId,
            this.scheduleId,
            this.totalAmount
        );

        // 상태별 처리
        if (this.status == ReservationStatus.CONFIRMED) {
            reservation = reservation.confirm();
        } else if (this.status == ReservationStatus.CANCELLED) {
            reservation = reservation.cancel();
        } else if (this.status == ReservationStatus.EXPIRED) {
            reservation = reservation.expire();
        }
        // PENDING은 기본 상태이므로 별도 처리 불필요

        // ID 할당
        reservation.assignId(this.id);

        return reservation;
    }

    /**
     * Domain의 상태 변경을 Entity에 반영 (Dirty Checking용)
     */
    public void updateFrom(Reservation domain) {
        this.status = domain.getStatus();
    }
}
