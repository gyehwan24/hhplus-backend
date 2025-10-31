package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConcertRepositoryImpl implements ConcertRepository {

    private final ConcertJpaRepository jpaRepository;

    @Override
    public Optional<Concert> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Concert> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Concert save(Concert concert) {
        return jpaRepository.save(concert);
    }
}
