package kr.hhplus.be.server.concert.application.response;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;

import java.math.BigDecimal;

/**
 * 좌석 응답 DTO
 */
public class SeatResponse {

    private Long seatId;
    private Long scheduleId;
    private Long venueSeatId;
    private BigDecimal price;
    private SeatStatus status;

    // TODO: 생성자를 작성하세요
    // private SeatResponse(...) { ... }

    // TODO: Getter 메서드들을 작성하세요
    // public Long getSeatId() { return seatId; }
    // ...

    /**
     * 도메인 객체를 DTO로 변환
     *
     * TODO: 이 메서드를 구현하세요
     * 힌트: ScheduleSeat 객체의 필드들을 읽어서 SeatResponse 객체를 생성합니다
     */
    public static SeatResponse from(ScheduleSeat seat) {
        // TODO: 구현하세요
        return null;
    }
}
