package kr.hhplus.be.server.payment.interfaces;

import kr.hhplus.be.server.payment.application.PaymentService;
import kr.hhplus.be.server.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 컨트롤러
 * Kafka 통합 테스트용
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.processPayment(request.reservationId(), request.userId());
        return ResponseEntity.ok(new PaymentResponse(
                payment.getId(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name()
        ));
    }

    public record PaymentRequest(Long reservationId, Long userId) {}

    public record PaymentResponse(Long paymentId, Long reservationId, Long userId,
                                   java.math.BigDecimal amount, String status) {}
}
