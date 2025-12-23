package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        ReservationEntity entity;

        if (reservation.getId() != null) {
            // 기존 엔티티 조회 후 업데이트 (version 유지)
            entity = jpaRepository.findById(reservation.getId())
                    .orElseThrow(() -> new IllegalStateException("예약을 찾을 수 없습니다."));
            entity.updateFrom(reservation);
        } else {
            // 새 엔티티 생성
            entity = ReservationEntity.from(reservation);
        }

        // JPA로 저장
        ReservationEntity saved = jpaRepository.save(entity);

        // 불변성 준수: 새로운 인스턴스 반환 (기존 객체 수정 없음)
        return saved.toDomain();
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

    @Override
    public List<Reservation> findExpiredReservations(LocalDateTime now) {
        // Entity → Domain 변환
        return jpaRepository.findExpiredReservations(now, ReservationStatus.PENDING)
                           .stream()
                           .map(ReservationEntity::toDomain)
                           .toList();
    }

    @Override
    public int expireIfPendingAndExpired(LocalDateTime now) {
        return jpaRepository.expireIfPendingAndExpired(now);
    }
}
