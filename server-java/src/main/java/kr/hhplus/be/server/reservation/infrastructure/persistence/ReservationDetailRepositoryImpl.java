package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReservationDetailRepositoryImpl implements ReservationDetailRepository {

    private final ReservationDetailJpaRepository jpaRepository;

    @Override
    public ReservationDetail save(ReservationDetail detail) {
        return jpaRepository.save(detail);
    }

    @Override
    public List<ReservationDetail> saveAll(List<ReservationDetail> details) {
        return jpaRepository.saveAll(details);
    }

    @Override
    public List<ReservationDetail> findByReservationId(Long reservationId) {
        return jpaRepository.findByReservationId(reservationId);
    }
}
