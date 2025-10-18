package kr.hhplus.be.server.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Payment JPA Repository
 * - PaymentEntity와 JPA 통신
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByReservationId(Long reservationId);
}
