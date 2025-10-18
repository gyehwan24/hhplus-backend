package kr.hhplus.be.server.reservation.application;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
     *
     * @param userId 사용자 ID
     * @param scheduleId 콘서트 일정 ID
     * @param seatIds 예약할 좌석 ID 목록
     * @return 생성된 예약
     */
    public Reservation createReservation(Long userId, Long scheduleId, List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("예약할 좌석이 없습니다.");
        }

        List<ScheduleSeat> findSeats = seatRepository.findAllByIdWithLock(seatIds);

        if (findSeats.size() != seatIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 좌석이 포함되어 있습니다.");
        }

        findSeats.forEach(ScheduleSeat::reserve);

        BigDecimal totalAmount = findSeats.stream()
            .map(ScheduleSeat::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Reservation reservation = Reservation.create(
            userId, scheduleId, totalAmount  
        );

        Reservation savedReservation = reservationRepository.save(reservation);
        
        List<ReservationDetail> details = findSeats.stream()
            .map(seat -> ReservationDetail.create(
                savedReservation.getId(),
                seat.getId(),
                seat.getVenueSeatId().intValue(),
                seat.getPrice()
            ))
            .toList();
        reservationDetailRepository.saveAll(details);

        return savedReservation; 
    }
}
