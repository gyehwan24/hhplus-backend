package kr.hhplus.be.server.reservation.application.exception;

/**
 * 좌석 예약 불가 예외
 */
public class SeatNotAvailableException extends RuntimeException {
    public SeatNotAvailableException(String message) {
        super(message);
    }
}