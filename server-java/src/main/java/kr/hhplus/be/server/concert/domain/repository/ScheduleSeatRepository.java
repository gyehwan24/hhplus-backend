package kr.hhplus.be.server.concert.domain.repository;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;

import java.util.List;
import java.util.Optional;

public interface ScheduleSeatRepository {
    List<ScheduleSeat> findByScheduleId(Long scheduleId);
    Optional<ScheduleSeat> findById(Long id);
    List<ScheduleSeat> findAllById(List<Long> ids);
    List<ScheduleSeat> findAllByIdWithLock(List<Long> ids);
    List<ScheduleSeat> saveAll(List<ScheduleSeat> seats);

    /**
     * 만료된 예약 좌석 조회
     * - RESERVED 상태이면서 reservedUntil이 현재 시각보다 이전인 좌석
     * @return 만료된 예약 좌석 목록
     */
    List<ScheduleSeat> findExpiredReservedSeats();
}
