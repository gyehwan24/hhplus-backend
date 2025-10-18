package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReservationDetailRepositoryImpl implements ReservationDetailRepository {

    private final ReservationDetailJpaRepository jpaRepository;

    @Override
    public ReservationDetail save(ReservationDetail detail) {
        // Domain → Entity 변환
        ReservationDetailEntity entity = ReservationDetailEntity.from(detail);

        // JPA로 저장
        ReservationDetailEntity saved = jpaRepository.save(entity);

        // 생성된 ID를 Domain에 할당
        detail.assignId(saved.getId());

        return detail;
    }

    @Override
    public List<ReservationDetail> saveAll(List<ReservationDetail> details) {
        // Domain → Entity 변환
        List<ReservationDetailEntity> entities = details.stream()
                .map(ReservationDetailEntity::from)
                .collect(Collectors.toList());

        // JPA로 저장
        List<ReservationDetailEntity> saved = jpaRepository.saveAll(entities);

        // 생성된 ID를 각 Domain에 할당
        for (int i = 0; i < details.size(); i++) {
            details.get(i).assignId(saved.get(i).getId());
        }

        return details;
    }

    @Override
    public List<ReservationDetail> findAllByReservationId(Long reservationId) {
        // Entity → Domain 변환
        return jpaRepository.findByReservationId(reservationId).stream()
                .map(ReservationDetailEntity::toDomain)
                .collect(Collectors.toList());
    }
}
