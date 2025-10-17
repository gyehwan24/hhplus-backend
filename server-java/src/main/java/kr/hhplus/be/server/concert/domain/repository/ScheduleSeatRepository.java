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
}
