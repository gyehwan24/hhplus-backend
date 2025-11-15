package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, Long> {

    List<ScheduleSeat> findByScheduleId(Long scheduleId);

    /**
     * 비관적 락으로 좌석 조회
     * - 락 타임아웃: 3초 (무한 대기 방지)
     * @param ids 좌석 ID 목록
     * @return 조회된 좌석 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT ss FROM ScheduleSeat ss WHERE ss.id IN :ids")
    List<ScheduleSeat> findAllByIdWithLock(@Param("ids") List<Long> ids);

    /**
     * 예약 가능한 좌석 조회 (비관적 락)
     * - scheduleId와 일치하고 status가 AVAILABLE인 좌석만 조회
     * - 락 타임아웃: 3초, ID 순 정렬로 데드락 방지
     * @param scheduleId 스케줄 ID
     * @param ids 좌석 ID 목록
     * @return 예약 가능한 좌석 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT ss FROM ScheduleSeat ss " +
           "WHERE ss.scheduleId = :scheduleId " +
           "AND ss.id IN :ids " +
           "AND ss.status = 'AVAILABLE' " +
           "ORDER BY ss.id ASC")
    List<ScheduleSeat> findAvailableByScheduleIdAndIdWithLock(
        @Param("scheduleId") Long scheduleId,
        @Param("ids") List<Long> ids
    );

    /**
     * 만료된 예약 좌석 조회
     * - RESERVED 상태이면서 reservedUntil이 현재 시각보다 이전
     */
    @Query("SELECT s FROM ScheduleSeat s WHERE s.status = 'RESERVED' AND s.reservedUntil < :now")
    List<ScheduleSeat> findExpiredReservedSeats(@Param("now") LocalDateTime now);

    /**
     * 조건부 UPDATE: 특정 좌석들을 RESERVED → AVAILABLE로 변경
     * - WHERE절에 id 목록과 status = RESERVED 조건 포함
     * - @Version으로 optimistic lock 자동 적용
     * @param seatIds 해제할 좌석 ID 목록
     * @return 업데이트된 좌석 수
     */
    @Modifying
    @Query("UPDATE ScheduleSeat s SET s.status = 'AVAILABLE', s.reservedUntil = NULL " +
           "WHERE s.id IN :seatIds AND s.status = 'RESERVED'")
    int releaseSeatsIfReserved(@Param("seatIds") List<Long> seatIds);
}
