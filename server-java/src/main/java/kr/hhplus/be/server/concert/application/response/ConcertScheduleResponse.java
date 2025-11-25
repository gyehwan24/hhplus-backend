package kr.hhplus.be.server.concert.application.response;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 콘서트 일정 응답 DTO
 */
public record ConcertScheduleResponse(
    Long scheduleId,
    Long concertId,
    Long venueId,
    LocalDate performanceDate,
    LocalTime performanceTime,
    LocalDateTime bookingOpenAt,
    LocalDateTime bookingCloseAt,
    Integer maxSeatsPerUser,
    ScheduleStatus status
) implements Serializable {
    /**
     * 도메인 객체를 DTO로 변환
     *
     * @param schedule 콘서트 일정 도메인 객체
     * @return 콘서트 일정 응답 DTO
     */
    public static ConcertScheduleResponse from(ConcertSchedule schedule) {
        return new ConcertScheduleResponse(
            schedule.getId(),
            schedule.getConcertId(),
            schedule.getVenueId(),
            schedule.getPerformanceDate(),
            schedule.getPerformanceTime(),
            schedule.getBookingOpenAt(),
            schedule.getBookingCloseAt(),
            schedule.getMaxSeatsPerUser(),
            schedule.getStatus()
        );
    }
}
