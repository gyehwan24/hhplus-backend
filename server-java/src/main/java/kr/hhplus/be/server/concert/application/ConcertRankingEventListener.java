package kr.hhplus.be.server.concert.application;

import kr.hhplus.be.server.concert.infrastructure.redis.ConcertRankingRedisRepository;
import kr.hhplus.be.server.payment.domain.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 콘서트 랭킹 이벤트 리스너
 * 결제 완료 이벤트를 수신하여 랭킹 업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertRankingEventListener {

    private final ConcertRankingRedisRepository rankingRedisRepository;

    /**
     * 결제 완료 이벤트 처리
     * 트랜잭션 커밋 후에만 실행되어 데이터 정합성 보장
     *
     * @param event 결제 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            rankingRedisRepository.incrementScore(event.concertId());
            log.debug("랭킹 업데이트 완료 - concertId: {}", event.concertId());
        } catch (Exception e) {
            // 랭킹 업데이트 실패가 결제에 영향을 주면 안 됨
            log.error("랭킹 업데이트 실패 - concertId: {}, reservationId: {}",
                    event.concertId(), event.reservationId(), e);
        }
    }
}
