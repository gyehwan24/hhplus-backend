package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 비관적 락으로 좌석 조회 (동시성 제어)
     * - 락 타임아웃: 3초 (무한 대기 방지)
     * - 데드락 발생 시 LockTimeoutException 발생
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT ss FROM ScheduleSeat ss WHERE ss.id IN :ids")
    List<ScheduleSeat> findAllByIdWithLock(@Param("ids") List<Long> ids);

    /**
     * 만료된 예약 좌석 조회
     * - RESERVED 상태이면서 reservedUntil이 현재 시각보다 이전
     */
    @Query("SELECT s FROM ScheduleSeat s WHERE s.status = 'RESERVED' AND s.reservedUntil < :now")
    List<ScheduleSeat> findExpiredReservedSeats(@Param("now") LocalDateTime now);
}
