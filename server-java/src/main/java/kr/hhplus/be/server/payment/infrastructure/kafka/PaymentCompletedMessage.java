package kr.hhplus.be.server.payment.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka 결제 완료 메시지
 * 데이터 플랫폼으로 전송되는 예약 정보
 */
public record PaymentCompletedMessage(
    Long paymentId,
    Long reservationId,
    Long userId,
    Long concertId,
    Long scheduleId,
    BigDecimal amount,
    List<SeatInfo> seats,
    LocalDateTime completedAt
) {
    public record SeatInfo(
        Long seatId,
        Integer seatNumber,
        BigDecimal price
    ) {}
}
