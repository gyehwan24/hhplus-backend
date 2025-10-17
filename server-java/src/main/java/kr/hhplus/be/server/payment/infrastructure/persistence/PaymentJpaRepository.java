package kr.hhplus.be.server.payment.infrastructure.persistence;

import kr.hhplus.be.server.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReservationId(Long reservationId);
}
