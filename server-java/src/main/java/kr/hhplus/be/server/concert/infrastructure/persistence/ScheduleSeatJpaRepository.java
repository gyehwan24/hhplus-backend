package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;

public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, Long> {

    List<ScheduleSeat> findByScheduleId(Long scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ScheduleSeat ss WHERE ss.id IN :ids")
    List<ScheduleSeat> findAllByIdWithLock(@Param("ids") List<Long> ids);
}
