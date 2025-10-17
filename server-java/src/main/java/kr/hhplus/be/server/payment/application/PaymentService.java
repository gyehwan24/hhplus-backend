package kr.hhplus.be.server.payment.application;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 서비스
 * 예약에 대한 결제를 처리하고 관련 도메인(잔액, 예약, 좌석)을 업데이트
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;

    @Transactional
    public Payment processPayment(Long reservationId, Long userId) {

        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("예약한 사용자가 아닙니다.");
        }

        List<ReservationDetail> reservationDetails = reservationDetailRepository.findAllByReservationId(reservationId);
        List<Long> seatIds = reservationDetails.stream()
            .map(ReservationDetail::getSeatId)
            .toList();

        List<ScheduleSeat> seats = scheduleSeatRepository.findAllById(seatIds);

        UserBalance userBalance = userBalanceRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다."));

        userBalance.use(reservation.getTotalAmount());

        reservation.confirm();

        seats.forEach(ScheduleSeat::confirm);

        Payment payment = Payment.complete(reservationId, userId, reservation.getTotalAmount());
        return paymentRepository.save(payment);
    }

    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
    }

    public Payment getPaymentByReservationId(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("해당 예약의 결제 정보를 찾을 수 없습니다."));
    }
}
