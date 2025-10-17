package kr.hhplus.be.server.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 상세 엔티티
 * 예약된 개별 좌석 정보를 관리
 */
@Entity
@Table(name = "reservation_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReservationDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reservationId;

    private Long seatId;

    private Integer seatNumber;

    private BigDecimal price;

    @CreatedDate
    private LocalDateTime createdAt;

    @Builder
    public ReservationDetail(Long reservationId, Long seatId,
                            Integer seatNumber, BigDecimal price) {
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.price = price;
    }

    public static ReservationDetail create(Long reservationId, Long seatId,
                                          Integer seatNumber, BigDecimal price) {
        return ReservationDetail.builder()
            .reservationId(reservationId)
            .seatId(seatId)
            .seatNumber(seatNumber)
            .price(price)
            .build();
    }
}
