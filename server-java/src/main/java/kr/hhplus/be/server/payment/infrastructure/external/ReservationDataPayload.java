package kr.hhplus.be.server.payment.infrastructure.external;

import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 데이터 플랫폼 전송용 페이로드
 */
public record ReservationDataPayload(
    Long reservationId,
    Long userId,
    Long concertId,
    Long scheduleId,
    BigDecimal totalAmount,
    List<SeatPayload> seats,
    LocalDateTime completedAt
) {
    /**
     * 좌석 정보 페이로드
     */
    public record SeatPayload(
        Long seatId,
        Integer seatNumber,
        BigDecimal price
    ) {}

    /**
     * PaymentCompletedEvent로부터 페이로드 생성
     */
    public static ReservationDataPayload from(PaymentCompletedEvent event) {
        List<SeatPayload> seats = event.seats().stream()
            .map(s -> new SeatPayload(s.seatId(), s.seatNumber(), s.price()))
            .toList();

        return new ReservationDataPayload(
            event.reservationId(),
            event.userId(),
            event.concertId(),
            event.scheduleId(),
            event.amount(),
            seats,
            event.completedAt()
        );
    }
}
