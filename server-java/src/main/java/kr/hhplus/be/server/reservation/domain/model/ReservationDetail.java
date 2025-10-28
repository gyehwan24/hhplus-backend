package kr.hhplus.be.server.reservation.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 상세 도메인 모델 (POJO)
 * 예약된 개별 좌석 정보를 관리
 * JPA 의존성 없이 순수 비즈니스 로직만 포함
 */
public class ReservationDetail {

    private final Long id;
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
     * 영속성 계층에서 저장된 데이터를 도메인 객체로 재구성 (Reconstitute Pattern)
     * - 비즈니스 로직 없이 순수하게 상태만 복원
     * - Infrastructure 계층 전용 메서드
     * - 이미 저장된 데이터는 유효하다고 가정하므로 검증 없이 생성
     *
     * @param id 저장된 ID
     * @param reservationId 예약 ID
     * @param seatId 좌석 ID
     * @param seatNumber 좌석 번호
     * @param price 좌석 가격
     * @param createdAt 생성 시간
     * @return 재구성된 도메인 객체
     */
    public static ReservationDetail reconstitute(Long id, Long reservationId, Long seatId,
                                                Integer seatNumber, BigDecimal price,
                                                LocalDateTime createdAt) {
        return new ReservationDetail(id, reservationId, seatId, seatNumber, price, createdAt);
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
