package kr.hhplus.be.server.reservation.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 상세 JPA 엔티티
 * Domain ↔ Entity 변환 담당 (Infrastructure)
 */
@Entity
@Table(name = "reservation_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
class ReservationDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long seatId;

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Domain → Entity 변환
     */
    public static ReservationDetailEntity from(ReservationDetail domain) {
        ReservationDetailEntity entity = new ReservationDetailEntity();
        entity.reservationId = domain.getReservationId();
        entity.seatId = domain.getSeatId();
        entity.seatNumber = domain.getSeatNumber();
        entity.price = domain.getPrice();
        return entity;
    }

    /**
     * Entity → Domain 변환
     */
    public ReservationDetail toDomain() {
        ReservationDetail detail = ReservationDetail.create(
            this.reservationId,
            this.seatId,
            this.seatNumber,
            this.price
        );

        // ID 할당
        detail.assignId(this.id);

        return detail;
    }
}
