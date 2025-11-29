package kr.hhplus.be.server.payment.domain.event;

/**
 * 결제 완료 이벤트
 * 결제가 성공적으로 완료되었을 때 발행됨
 */
public record PaymentCompletedEvent(
    Long concertId
) {
    public static PaymentCompletedEvent of(Long concertId) {
        return new PaymentCompletedEvent(concertId);
    }
}
