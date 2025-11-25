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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
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
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private static final String SEAT_CACHE_NAME = "cache:seat:available";

    private final ScheduleSeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final CacheManager cacheManager;

    /**
     * 예약 생성
     * 
     * @param userId     사용자 ID
     * @param scheduleId 콘서트 일정 ID
     * @param seatIds    예약할 좌석 ID 목록
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
                    scheduleId, sortedSeatIds);

            if (seats.size() != seatIds.size()) {
                // 명확한 실패 이유 제공
                Set<Long> foundIds = seats.stream()
                        .map(ScheduleSeat::getId)
                        .collect(Collectors.toSet());
                Set<Long> notFoundIds = new HashSet<>(seatIds);
                notFoundIds.removeAll(foundIds);

                throw new SeatNotAvailableException(
                        String.format("좌석 %s는 예약할 수 없습니다 (이미 예약됨 또는 다른 스케줄)", notFoundIds));
            }

            seats.forEach(ScheduleSeat::reserve);

            BigDecimal totalAmount = seats.stream()
                    .map(ScheduleSeat::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Reservation reservation = Reservation.create(
                    userId, scheduleId, totalAmount);

            Reservation savedReservation = reservationRepository.save(reservation);

            List<ReservationDetail> details = seats.stream()
                    .map(seat -> ReservationDetail.create(
                            savedReservation.getId(),
                            seat.getId(),
                            seat.getVenueSeatId().intValue(),
                            seat.getPrice()))
                    .toList();
            reservationDetailRepository.saveAll(details);

            // 좌석 캐시 무효화 (Write-Through)
            evictSeatCache(scheduleId);

            return savedReservation;

        } catch (PessimisticLockingFailureException e) {
            throw new ConcurrentReservationException("다른 사용자가 예약 중입니다. 잠시 후 다시 시도해주세요");
        }
    }

    /**
     * 만료된 예약을 처리하고 좌석을 해제
     * - 조건부 UPDATE로 race condition 방지
     * - 결제와 만료 배치가 동시 실행되어도 안전
     * 
     * @return 만료 처리된 예약 수
     */
    public int expireReservationsAndReleaseSeats() {
        LocalDateTime now = LocalDateTime.now();

        try {
            // Step 1: 만료된 예약 조회 (좌석 ID 수집용)
            List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(now);

            if (expiredReservations.isEmpty()) {
                log.debug("만료된 예약이 없습니다.");
                return 0;
            }

            // Step 2: 좌석 ID 수집
            List<Long> seatIds = expiredReservations.stream()
                    .flatMap(reservation -> {
                        List<ReservationDetail> details = reservationDetailRepository
                                .findAllByReservationId(reservation.getId());
                        return details.stream().map(ReservationDetail::getSeatId);
                    })
                    .toList();

            // Step 3: 조건부 UPDATE로 예약 만료 (PENDING → CANCELLED)
            int expiredCount = reservationRepository.expireIfPendingAndExpired(now);

            // Step 4: 조건부 UPDATE로 좌석 해제 (RESERVED → AVAILABLE)
            int releasedCount = seatRepository.releaseSeatsIfReserved(seatIds);

            // Step 5: 좌석 캐시 무효화
            expiredReservations.stream()
                    .map(Reservation::getScheduleId)
                    .distinct()
                    .forEach(this::evictSeatCache);

            log.info("예약 만료 처리 완료 - 예약: {}건, 좌석: {}건", expiredCount, releasedCount);
            return expiredCount;

        } catch (OptimisticLockingFailureException e) {
            // 다른 트랜잭션(결제)이 먼저 처리한 경우
            log.warn("예약 만료 처리 중 충돌 발생 (다른 트랜잭션이 먼저 처리): {}", e.getMessage());
            return 0;
        }
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
