package kr.hhplus.be.server.payment.infrastructure.persistence;

import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByReservationId(Long reservationId) {
        return jpaRepository.findByReservationId(reservationId);
    }
}
