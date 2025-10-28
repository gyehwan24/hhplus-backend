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

    private final Long id;
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
     * 영속성 계층에서 저장된 데이터를 도메인 객체로 재구성 (Reconstitute Pattern)
     * - 비즈니스 로직 없이 순수하게 상태만 복원
     * - Infrastructure 계층 전용 메서드
     * - 이미 저장된 데이터는 유효하다고 가정하므로 검증 없이 생성
     *
     * @param id 저장된 ID
     * @param userId 사용자 ID
     * @param scheduleId 스케줄 ID
     * @param totalAmount 예약 금액
     * @param status 예약 상태 (모든 상태 허용)
     * @param expiresAt 만료 시간
     * @param createdAt 생성 시간
     * @param updatedAt 수정 시간
     * @return 재구성된 도메인 객체
     */
    public static Reservation reconstitute(Long id, Long userId, Long scheduleId,
                                          BigDecimal totalAmount, ReservationStatus status,
                                          LocalDateTime expiresAt, LocalDateTime createdAt,
                                          LocalDateTime updatedAt) {
        return new Reservation(id, userId, scheduleId, totalAmount, status,
                             expiresAt, createdAt, updatedAt);
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
