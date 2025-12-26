package kr.hhplus.be.server.payment.application;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 서비스
 * 예약에 대한 결제를 처리하고 관련 도메인(잔액, 예약, 좌석)을 업데이트
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private static final String SEAT_CACHE_NAME = "cache:seat:available";

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final ConcertScheduleRepository concertScheduleRepository;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

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

        Reservation confirmedReservation = reservation.confirm();
        reservationRepository.save(confirmedReservation);

        seats.forEach(ScheduleSeat::confirm);

        // 좌석 캐시 무효화 (결제 완료 시 좌석 상태 변경)
        evictSeatCache(reservation.getScheduleId());

        Payment payment = Payment.complete(reservationId, userId, reservation.getTotalAmount());
        Payment savedPayment = paymentRepository.save(payment);

        // 결제 완료 이벤트 발행 (랭킹 업데이트 + 데이터 플랫폼 전송)
        ConcertSchedule schedule = concertScheduleRepository.findById(reservation.getScheduleId())
                .orElseThrow(() -> new IllegalStateException("스케줄을 찾을 수 없습니다."));

        List<PaymentCompletedEvent.SeatInfo> seatInfos = seats.stream()
                .map(seat -> new PaymentCompletedEvent.SeatInfo(
                        seat.getId(),
                        seat.getVenueSeatId().intValue(),
                        seat.getPrice()))
                .toList();

        eventPublisher.publishEvent(PaymentCompletedEvent.of(
                savedPayment.getId(),
                reservationId,
                userId,
                schedule.getConcertId(),
                reservation.getScheduleId(),
                reservation.getTotalAmount(),
                seatInfos
        ));

        return savedPayment;
    }

    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
    }

    public Payment getPaymentByReservationId(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약의 결제 정보를 찾을 수 없습니다."));
    }

    /**
     * 좌석 캐시 무효화
     *
     * @param scheduleId 스케줄 ID
     */
    private void evictSeatCache(Long scheduleId) {
        Cache cache = cacheManager.getCache(SEAT_CACHE_NAME);
        if (cache != null) {
            cache.evict(scheduleId);
            log.debug("좌석 캐시 무효화 - scheduleId: {}", scheduleId);
        }
    }
}
