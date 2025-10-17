package kr.hhplus.be.server.reservation.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
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
 * 예약 엔티티
 * 사용자의 콘서트 예약 정보를 관리
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long scheduleId;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime expiresAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Reservation(Long userId, Long scheduleId, BigDecimal totalAmount,
                      ReservationStatus status, LocalDateTime expiresAt) {
        this.userId = userId;
        this.scheduleId = scheduleId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static Reservation create(Long userId, Long scheduleId, BigDecimal totalAmount) {
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("예약 금액은 양수여야 합니다.");
        }
        return Reservation.builder()
            .userId(userId)
            .scheduleId(scheduleId)
            .totalAmount(totalAmount)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build();
    }

    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("예약 대기 상태에서만 예약을 확정할 수 있습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("예약 대기 상태에서만 예약 만료 가능합니다.");
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(this.expiresAt)) {
            return true;
        }
        return false;
    }
}
