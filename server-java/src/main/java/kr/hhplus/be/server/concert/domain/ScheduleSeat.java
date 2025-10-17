package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_seats")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scheduleId;
    private Long venueSeatId;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private LocalDateTime reservedUntil;

    @Version
    private Integer version;  // Optimistic Lock

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isAvailable() {
        return this.status == SeatStatus.AVAILABLE;
    }

    public void reserve() {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("예약 가능 상태가 아닙니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.reservedUntil = LocalDateTime.now().plusMinutes(10);
    }

    public void confirm() {
        if (this.status != SeatStatus.RESERVED) {
            throw new IllegalStateException("확정 가능 상태가 아닙니다.");
        }
        this.status = SeatStatus.SOLD;
    }

    public void release() {
        if (this.status != SeatStatus.RESERVED) {
            throw new IllegalStateException("예약 해제 가능 상태가 아닙니다.");
        }
        this.status = SeatStatus.AVAILABLE;
    }

    @Builder
    public ScheduleSeat(Long scheduleId, Long venueSeatId, BigDecimal price, SeatStatus status) {
        this.scheduleId = scheduleId;
        this.venueSeatId = venueSeatId;
        this.price = price;
        this.status = status != null ? status : SeatStatus.AVAILABLE;
    }
}
