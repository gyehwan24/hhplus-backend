package kr.hhplus.be.server.reservation.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 상세 도메인 모델 (POJO)
 * 예약된 개별 좌석 정보를 관리
 * JPA 의존성 없이 순수 비즈니스 로직만 포함
 */
public class ReservationDetail {

    private Long id;
    private final Long reservationId;
    private final Long seatId;
    private final Integer seatNumber;
    private final BigDecimal price;
    private final LocalDateTime createdAt;

    private ReservationDetail(Long id, Long reservationId, Long seatId,
                             Integer seatNumber, BigDecimal price,
                             LocalDateTime createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.createdAt = createdAt;
    }

    /**
     * 예약 상세 생성
     */
    public static ReservationDetail create(Long reservationId, Long seatId,
                                          Integer seatNumber, BigDecimal price) {
        validateReservationDetailCreation(reservationId, seatId, seatNumber, price);
        return new ReservationDetail(
            null,
            reservationId,
            seatId,
            seatNumber,
            price,
            LocalDateTime.now()
        );
    }

    /**
     * Infrastructure를 위한 ID 할당
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 한 번만 할당할 수 있습니다.");
        }
        this.id = id;
    }

    /**
     * 예약 상세 생성 검증
     */
    private static void validateReservationDetailCreation(Long reservationId, Long seatId,
                                                         Integer seatNumber, BigDecimal price) {
        if (reservationId == null) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        if (seatId == null) {
            throw new IllegalArgumentException("좌석 ID는 필수입니다.");
        }
        if (seatNumber == null || seatNumber <= 0) {
            throw new IllegalArgumentException("좌석 번호는 양수여야 합니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("좌석 가격은 양수여야 합니다.");
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
