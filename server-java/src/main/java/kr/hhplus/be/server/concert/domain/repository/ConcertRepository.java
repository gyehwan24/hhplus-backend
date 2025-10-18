package kr.hhplus.be.server.concert.domain.repository;

import kr.hhplus.be.server.concert.domain.Concert;

import java.util.List;
import java.util.Optional;

public interface ConcertRepository {
    Optional<Concert> findById(Long id);
    List<Concert> findAll();
    Concert save(Concert save);
}
