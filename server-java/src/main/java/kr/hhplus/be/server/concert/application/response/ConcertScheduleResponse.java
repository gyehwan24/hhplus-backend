package kr.hhplus.be.server.concert.application.response;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 콘서트 일정 응답 DTO
 */
public class ConcertScheduleResponse {

    private Long scheduleId;
    private Long concertId;
    private Long venueId;
    private LocalDate performanceDate;
    private LocalTime performanceTime;
    private LocalDateTime bookingOpenAt;
    private LocalDateTime bookingCloseAt;
    private Integer maxSeatsPerUser;
    private ScheduleStatus status;

    // TODO: 생성자를 작성하세요
    // private ConcertScheduleResponse(...) { ... }

    // TODO: Getter 메서드들을 작성하세요
    // public Long getScheduleId() { return scheduleId; }
    // ...

    /**
     * 도메인 객체를 DTO로 변환
     *
     * TODO: 이 메서드를 구현하세요
     * 힌트: ConcertSchedule 객체의 필드들을 읽어서 ConcertScheduleResponse 객체를 생성합니다
     */
    public static ConcertScheduleResponse from(ConcertSchedule schedule) {
        // TODO: 구현하세요
        return null;
    }
}
