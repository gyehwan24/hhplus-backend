package kr.hhplus.be.server.reservation.domain.enums;

/**
 * 예약 상태
 */
public enum ReservationStatus {
    PENDING,        // 예약 대기 (결제 전)
    CONFIRMED,      // 예약 확정 (결제 완료)
    CANCELLED,      // 예약 취소
    EXPIRED         // 예약 만료 (시간 초과)
}
