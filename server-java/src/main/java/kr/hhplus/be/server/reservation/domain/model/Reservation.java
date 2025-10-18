package kr.hhplus.be.server.reservation.domain.model;

import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 도메인 모델 (POJO)
 * 사용자의 콘서트 예약 정보를 관리
 * JPA 의존성 없이 순수 비즈니스 로직만 포함
 */
public class Reservation {

    private Long id;
    private final Long userId;
    private final Long scheduleId;
    private final BigDecimal totalAmount;
    private final ReservationStatus status;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Reservation(Long id, Long userId, Long scheduleId, BigDecimal totalAmount,
                       ReservationStatus status, LocalDateTime expiresAt,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.scheduleId = scheduleId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 예약 생성
     */
    public static Reservation create(Long userId, Long scheduleId, BigDecimal totalAmount) {
        validateReservationCreation(userId, scheduleId, totalAmount);
        LocalDateTime now = LocalDateTime.now();
        return new Reservation(
            null,
            userId,
            scheduleId,
            totalAmount,
            ReservationStatus.PENDING,
            now.plusMinutes(10),
            now,
            now
        );
    }

    /**
     * 예약 확정
     */
    public Reservation confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("예약 대기 상태에서만 예약을 확정할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        return new Reservation(
            this.id,
            this.userId,
            this.scheduleId,
            this.totalAmount,
            ReservationStatus.CONFIRMED,
            this.expiresAt,
            this.createdAt,
            now
        );
    }

    /**
     * 예약 취소
     */
    public Reservation cancel() {
        LocalDateTime now = LocalDateTime.now();
        return new Reservation(
            this.id,
            this.userId,
            this.scheduleId,
            this.totalAmount,
            ReservationStatus.CANCELLED,
            this.expiresAt,
            this.createdAt,
            now
        );
    }

    /**
     * 예약 만료
     */
    public Reservation expire() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("예약 대기 상태에서만 예약 만료 가능합니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        return new Reservation(
            this.id,
            this.userId,
            this.scheduleId,
            this.totalAmount,
            ReservationStatus.EXPIRED,
            this.expiresAt,
            this.createdAt,
            now
        );
    }

    /**
     * 예약 만료 여부 확인
     */
    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(this.expiresAt);
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
     * 예약 생성 검증
     */
    private static void validateReservationCreation(Long userId, Long scheduleId, BigDecimal totalAmount) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (scheduleId == null) {
            throw new IllegalArgumentException("스케줄 ID는 필수입니다.");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("예약 금액은 양수여야 합니다.");
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
