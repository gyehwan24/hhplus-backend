package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduleSeatRepositoryImpl implements ScheduleSeatRepository {

    private final ScheduleSeatJpaRepository jpaRepository;

    @Override
    public List<ScheduleSeat> findByScheduleId(Long scheduleId) {
        return jpaRepository.findByScheduleId(scheduleId);
    }

    @Override
    public Optional<ScheduleSeat> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ScheduleSeat> findAllById(List<Long> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public List<ScheduleSeat> findAllByIdWithLock(List<Long> ids) {
        return jpaRepository.findAllByIdWithLock(ids);
    }

    @Override
    public List<ScheduleSeat> saveAll(List<ScheduleSeat> seats) {
        return jpaRepository.saveAll(seats);
    }
}
