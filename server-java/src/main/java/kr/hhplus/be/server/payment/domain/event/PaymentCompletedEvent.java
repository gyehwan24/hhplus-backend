package kr.hhplus.be.server.payment.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 완료 이벤트
 * 결제가 성공적으로 완료되었을 때 발행됨
 *
 * 리스너:
 * - ConcertRankingEventListener: 랭킹 업데이트 (concertId 사용)
 * - DataPlatformEventListener: 데이터 플랫폼 전송 (전체 정보 사용)
 */
public record PaymentCompletedEvent(
    Long paymentId,
    Long reservationId,
    Long userId,
    Long concertId,
    Long scheduleId,
    BigDecimal amount,
    List<SeatInfo> seats,
    LocalDateTime completedAt
) {
    /**
     * 좌석 정보
     */
    public record SeatInfo(
        Long seatId,
        Integer seatNumber,
        BigDecimal price
    ) {}

    /**
     * 결제 완료 이벤트 생성
     */
    public static PaymentCompletedEvent of(
        Long paymentId,
        Long reservationId,
        Long userId,
        Long concertId,
        Long scheduleId,
        BigDecimal amount,
        List<SeatInfo> seats
    ) {
        return new PaymentCompletedEvent(
            paymentId,
            reservationId,
            userId,
            concertId,
            scheduleId,
            amount,
            seats,
            LocalDateTime.now()
        );
    }
}
