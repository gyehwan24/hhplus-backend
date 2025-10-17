package kr.hhplus.be.server.payment.domain.repository;

import kr.hhplus.be.server.payment.domain.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByReservationId(Long reservationId);
}
