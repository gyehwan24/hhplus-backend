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
     * 예약 가능한 좌석 조회 (비관적 락)
     * - scheduleId와 일치하고 status가 AVAILABLE인 좌석만 조회
     * @param scheduleId 스케줄 ID
     * @param ids 좌석 ID 목록
     * @return 예약 가능한 좌석 목록
     */
    List<ScheduleSeat> findAvailableByScheduleIdAndIdWithLock(Long scheduleId, List<Long> ids);

    /**
     * 만료된 예약 좌석 조회
     * - RESERVED 상태이면서 reservedUntil이 현재 시각보다 이전인 좌석
     * @return 만료된 예약 좌석 목록
     */
    List<ScheduleSeat> findExpiredReservedSeats();

    /**
     * 조건부 UPDATE: 특정 좌석들을 RESERVED 상태에서 AVAILABLE로 변경
     * - WHERE절에 id 목록과 status = RESERVED 조건 포함
     * - @Version으로 optimistic lock 자동 적용
     * @param seatIds 해제할 좌석 ID 목록
     * @return 업데이트된 좌석 수
     */
    int releaseSeatsIfReserved(List<Long> seatIds);
}
