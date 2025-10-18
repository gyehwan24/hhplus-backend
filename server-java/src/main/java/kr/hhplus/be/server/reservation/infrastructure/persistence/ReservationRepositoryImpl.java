package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        // Domain → Entity 변환
        ReservationEntity entity = ReservationEntity.from(reservation);

        // JPA로 저장
        ReservationEntity saved = jpaRepository.save(entity);

        // 생성된 ID를 Domain에 할당
        reservation.assignId(saved.getId());

        return reservation;
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        // Entity → Domain 변환
        return jpaRepository.findById(id)
                           .map(ReservationEntity::toDomain);
    }

    @Override
    public Optional<Reservation> findByUserId(Long userId) {
        // Entity → Domain 변환
        return jpaRepository.findByUserId(userId)
                           .map(ReservationEntity::toDomain);
    }
}
