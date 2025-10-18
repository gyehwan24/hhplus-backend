package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConcertScheduleRepositoryImpl implements ConcertScheduleRepository {

    private final ConcertScheduleJpaRepository jpaRepository;

    @Override
    public Optional<ConcertSchedule> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ConcertSchedule> findByConcertId(Long concertId) {
        return jpaRepository.findByConcertId(concertId);
    }

    @Override
    public List<ConcertSchedule> findByConcertIdAndDateRange(
        Long concertId, LocalDate fromDate, LocalDate toDate
    ) {
        return jpaRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate);
    }
}
