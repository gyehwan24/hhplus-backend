package kr.hhplus.be.server.reservation.application.exception;

/**
 * 동시 예약 충돌 예외
 */
public class ConcurrentReservationException extends RuntimeException {
    public ConcurrentReservationException(String message) {
        super(message);
    }
}