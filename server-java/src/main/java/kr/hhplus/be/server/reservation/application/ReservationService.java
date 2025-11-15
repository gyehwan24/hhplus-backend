package kr.hhplus.be.server.reservation.application;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.reservation.application.exception.ConcurrentReservationException;
import kr.hhplus.be.server.reservation.application.exception.SeatNotAvailableException;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 예약 서비스
 * 좌석 예약 비즈니스 로직을 처리
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ScheduleSeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;

    /**
     * 예약 생성
     * @param userId 사용자 ID
     * @param scheduleId 콘서트 일정 ID
     * @param seatIds 예약할 좌석 ID 목록
     * @return 생성된 예약
     */
    public Reservation createReservation(Long userId, Long scheduleId, List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("예약할 좌석이 없습니다.");
        }

        // 데드락 방지: ID 정렬
        List<Long> sortedSeatIds = seatIds.stream()
            .sorted()
            .toList();

        try {
            // 새로운 메서드 사용: 스케줄 ID와 상태 검증 포함
            List<ScheduleSeat> seats = seatRepository.findAvailableByScheduleIdAndIdWithLock(
                scheduleId, sortedSeatIds
            );

            if (seats.size() != seatIds.size()) {
                // 명확한 실패 이유 제공
                Set<Long> foundIds = seats.stream()
                    .map(ScheduleSeat::getId)
                    .collect(Collectors.toSet());
                Set<Long> notFoundIds = new HashSet<>(seatIds);
                notFoundIds.removeAll(foundIds);

                throw new SeatNotAvailableException(
                    String.format("좌석 %s는 예약할 수 없습니다 (이미 예약됨 또는 다른 스케줄)", notFoundIds)
                );
            }

            seats.forEach(ScheduleSeat::reserve);

            BigDecimal totalAmount = seats.stream()
                .map(ScheduleSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Reservation reservation = Reservation.create(
                userId, scheduleId, totalAmount
            );

            Reservation savedReservation = reservationRepository.save(reservation);

            List<ReservationDetail> details = seats.stream()
                .map(seat -> ReservationDetail.create(
                    savedReservation.getId(),
                    seat.getId(),
                    seat.getVenueSeatId().intValue(),
                    seat.getPrice()
                ))
                .toList();
            reservationDetailRepository.saveAll(details);

            return savedReservation;

        } catch (PessimisticLockingFailureException e) {
            throw new ConcurrentReservationException("다른 사용자가 예약 중입니다. 잠시 후 다시 시도해주세요");
        }
    }

    /**
     * 만료된 예약을 처리하고 좌석을 해제
     * - 만료 시간이 지났지만 아직 PENDING 상태인 예약들을 처리
     * @return 만료 처리된 예약 수
     */
    public int expireReservationsAndReleaseSeats() {
        LocalDateTime now = LocalDateTime.now();

        // 만료 시간이 지났지만 아직 PENDING 상태인 예약들을 조회
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(now);

        if (expiredReservations.isEmpty()) {
            return 0;
        }

        for (Reservation reservation : expiredReservations) {
            // 예약 만료 처리
            Reservation expiredReservation = reservation.expire();
            reservationRepository.save(expiredReservation);

            // 해당 예약의 좌석들을 해제
            List<ReservationDetail> details = reservationDetailRepository.findAllByReservationId(reservation.getId());
            List<Long> seatIds = details.stream()
                .map(ReservationDetail::getSeatId)
                .toList();

            List<ScheduleSeat> seats = seatRepository.findAllById(seatIds);
            seats.forEach(ScheduleSeat::release);
        }

        return expiredReservations.size();
    }
}
