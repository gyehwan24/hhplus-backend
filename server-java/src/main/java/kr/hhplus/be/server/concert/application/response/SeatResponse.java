package kr.hhplus.be.server.concert.application.response;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;

import java.math.BigDecimal;

/**
 * 좌석 응답 DTO
 */
public record SeatResponse(
    Long seatId,
    Long scheduleId,
    Long venueSeatId,
    BigDecimal price,
    SeatStatus status
) {
    /**
     * 도메인 객체를 DTO로 변환
     *
     * @param seat 좌석 도메인 객체
     * @return 좌석 응답 DTO
     */
    public static SeatResponse from(ScheduleSeat seat) {
        return new SeatResponse(
            seat.getId(),
            seat.getScheduleId(),
            seat.getVenueSeatId(),
            seat.getPrice(),
            seat.getStatus()
        );
    }
}
