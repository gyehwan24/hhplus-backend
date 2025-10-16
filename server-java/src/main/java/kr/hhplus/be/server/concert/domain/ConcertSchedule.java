package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "concert_schedules")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long concertId;
    private Long venueId;

    private LocalDate performanceDate;
    private LocalTime performanceTime;

    private LocalDateTime bookingOpenAt;
    private LocalDateTime bookingCloseAt;

    private Integer maxSeatsPerUser;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    // 비즈니스 로직: 예약 가능 기간 체크
    public boolean isBookingOpen() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(this.bookingOpenAt) 
            && now.isBefore(this.bookingCloseAt)
            && this.status == ScheduleStatus.AVAILABLE;
    }

    @Builder
    public ConcertSchedule(Long concertId, Long venueId,
                          LocalDate performanceDate, LocalTime performanceTime,
                          LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt,
                          Integer maxSeatsPerUser, ScheduleStatus status) {
        
        if (bookingOpenAt != null && bookingCloseAt != null 
            && bookingOpenAt.isAfter(bookingCloseAt)) {
            throw new IllegalArgumentException("예약 시작 시간이 종료 시간보다 늦을 수 없습니다.");
        }
        this.concertId = concertId;
        this.venueId = venueId;
        this.performanceDate = performanceDate;
        this.performanceTime = performanceTime;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.maxSeatsPerUser = maxSeatsPerUser != null ? maxSeatsPerUser : 4;
        this.status = status != null ? status : ScheduleStatus.AVAILABLE;
    }
}
