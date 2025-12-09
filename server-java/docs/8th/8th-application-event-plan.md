# 8주차 필수과제: Application Event 구현 계획

## 과제 요구사항

> 예약정보(콘서트)를 데이터 플랫폼에 전송(mock API 호출)하는 요구사항을 이벤트를 활용하여 트랜잭션과 관심사를 분리하여 서비스를 개선합니다.

---

## 현재 상태 분석

### 기존 이벤트 시스템
```
PaymentService.processPayment()
    └── eventPublisher.publishEvent(PaymentCompletedEvent.of(concertId))
            └── ConcertRankingEventListener.onPaymentCompleted()
                    └── Redis 랭킹 업데이트
```

### 기존 PaymentCompletedEvent
```java
public record PaymentCompletedEvent(Long concertId) {
    public static PaymentCompletedEvent of(Long concertId) {
        return new PaymentCompletedEvent(concertId);
    }
}
```

**문제점:**
- `concertId`만 포함되어 있어 데이터 플랫폼 전송에 필요한 정보 부족
- 예약 상세 정보(좌석, 금액, 사용자 등)가 없음

---

## 설계 방향

### 1. 이벤트 분리 전략

**선택지 A: 기존 이벤트 확장**
```
PaymentCompletedEvent (확장)
├── 랭킹 업데이트 리스너 (기존)
└── 데이터 플랫폼 전송 리스너 (신규)
```

**선택지 B: 별도 이벤트 생성**
```
PaymentCompletedEvent (기존 - 랭킹용)
ReservationCompletedEvent (신규 - 데이터 플랫폼용)
```

**결정: 선택지 A (기존 이벤트 확장)**

**이유:**
- "결제 완료"는 하나의 비즈니스 이벤트
- 하나의 이벤트에 여러 리스너가 반응하는 것이 이벤트 기반 아키텍처의 본질
- 단일 책임 원칙: 이벤트는 "무엇이 발생했는가"를 표현, 리스너는 "어떻게 반응할 것인가"를 담당

### 2. 이벤트 발행 시점

| 시점 | 장점 | 단점 |
|------|------|------|
| 트랜잭션 내부 | 즉각적 발행 | 롤백 시 이벤트 발행됨 |
| **AFTER_COMMIT** | 트랜잭션 성공 시에만 발행 | 약간의 지연 |

**결정: `@TransactionalEventListener(phase = AFTER_COMMIT)`**

### 3. 동기 vs 비동기

| 방식 | 장점 | 단점 |
|------|------|------|
| 동기 | 간단, 즉각적 실패 감지 | 결제 응답 지연 |
| **비동기** | 결제 응답 빠름, 독립적 | 실패 처리 복잡 |

**결정: 비동기 (`@Async`)**

**이유:**
- 데이터 플랫폼 전송 실패가 결제 성공에 영향을 주면 안 됨
- 사용자 응답 시간 최소화
- 외부 API 호출은 지연/실패 가능성 높음

---

## 구현 계획

### Phase 1: 이벤트 확장

#### 1.1 PaymentCompletedEvent 확장
**파일**: `payment/domain/event/PaymentCompletedEvent.java`

```java
public record PaymentCompletedEvent(
    Long paymentId,
    Long reservationId,
    Long userId,
    Long concertId,
    Long scheduleId,
    BigDecimal amount,
    List<SeatInfo> seats,
    LocalDateTime completedAt
) {
    public record SeatInfo(
        Long seatId,
        Integer seatNumber,
        BigDecimal price
    ) {}

    // 기존 호환성을 위한 팩토리 메서드 유지
    public static PaymentCompletedEvent of(Long concertId) {
        return new PaymentCompletedEvent(
            null, null, null, concertId, null, null, List.of(), LocalDateTime.now()
        );
    }

    // 전체 정보를 포함한 팩토리 메서드
    public static PaymentCompletedEvent of(
        Long paymentId,
        Long reservationId,
        Long userId,
        Long concertId,
        Long scheduleId,
        BigDecimal amount,
        List<SeatInfo> seats
    ) {
        return new PaymentCompletedEvent(
            paymentId, reservationId, userId, concertId, scheduleId,
            amount, seats, LocalDateTime.now()
        );
    }
}
```

#### 1.2 PaymentService 수정
**파일**: `payment/application/PaymentService.java`

**변경 내용:**
- 이벤트 발행 시 전체 예약 정보 포함

```java
// 결제 완료 이벤트 발행 (확장된 정보)
List<PaymentCompletedEvent.SeatInfo> seatInfos = reservationDetails.stream()
    .map(detail -> new PaymentCompletedEvent.SeatInfo(
        detail.getSeatId(),
        detail.getSeatNumber(),
        detail.getPrice()
    ))
    .toList();

eventPublisher.publishEvent(PaymentCompletedEvent.of(
    savedPayment.getId(),
    reservationId,
    userId,
    schedule.getConcertId(),
    reservation.getScheduleId(),
    reservation.getTotalAmount(),
    seatInfos
));
```

---

### Phase 2: 데이터 플랫폼 전송 서비스 구현

#### 2.1 DataPlatformClient (Mock API)
**파일**: `payment/infrastructure/external/DataPlatformClient.java`

```java
@Component
@Slf4j
public class DataPlatformClient {

    /**
     * 데이터 플랫폼에 예약 정보 전송 (Mock)
     * 실제 환경에서는 RestTemplate, WebClient 등 사용
     */
    public void sendReservationData(ReservationDataPayload payload) {
        log.info("[DataPlatform] 예약 데이터 전송 시작: reservationId={}", payload.reservationId());

        // Mock: 실제 API 호출 시뮬레이션
        try {
            Thread.sleep(100); // API 호출 지연 시뮬레이션
            log.info("[DataPlatform] 예약 데이터 전송 완료: {}", payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("데이터 플랫폼 전송 중 인터럽트", e);
        }
    }
}
```

#### 2.2 ReservationDataPayload (전송 데이터)
**파일**: `payment/infrastructure/external/ReservationDataPayload.java`

```java
public record ReservationDataPayload(
    Long reservationId,
    Long userId,
    Long concertId,
    Long scheduleId,
    BigDecimal totalAmount,
    List<SeatPayload> seats,
    LocalDateTime completedAt
) {
    public record SeatPayload(
        Long seatId,
        Integer seatNumber,
        BigDecimal price
    ) {}

    public static ReservationDataPayload from(PaymentCompletedEvent event) {
        List<SeatPayload> seats = event.seats().stream()
            .map(s -> new SeatPayload(s.seatId(), s.seatNumber(), s.price()))
            .toList();

        return new ReservationDataPayload(
            event.reservationId(),
            event.userId(),
            event.concertId(),
            event.scheduleId(),
            event.amount(),
            seats,
            event.completedAt()
        );
    }
}
```

---

### Phase 3: 이벤트 리스너 구현

#### 3.1 DataPlatformEventListener
**파일**: `payment/application/event/DataPlatformEventListener.java`

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final DataPlatformClient dataPlatformClient;

    /**
     * 결제 완료 이벤트 처리 - 데이터 플랫폼 전송
     *
     * - @Async: 비동기로 실행하여 결제 응답 지연 방지
     * - @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후에만 실행
     * - 실패해도 결제에 영향 없음 (try-catch)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("[DataPlatformListener] 이벤트 수신: reservationId={}", event.reservationId());

            ReservationDataPayload payload = ReservationDataPayload.from(event);
            dataPlatformClient.sendReservationData(payload);

            log.info("[DataPlatformListener] 전송 완료: reservationId={}", event.reservationId());
        } catch (Exception e) {
            // 데이터 플랫폼 전송 실패가 결제에 영향을 주면 안 됨
            log.error("[DataPlatformListener] 전송 실패: reservationId={}", event.reservationId(), e);
            // TODO: 재시도 큐 등록 또는 알림 발송
        }
    }
}
```

---

### Phase 4: 비동기 설정

#### 4.1 AsyncConfig
**파일**: `config/async/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-Event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            // 비동기 예외 로깅
            LoggerFactory.getLogger(AsyncConfig.class)
                .error("Async 예외 발생 - method: {}, params: {}", method.getName(), params, ex);
        };
    }
}
```

---

### Phase 5: 테스트

#### 5.1 단위 테스트
**파일**: `test/payment/application/event/DataPlatformEventListenerTest.java`

```java
@ExtendWith(MockitoExtension.class)
class DataPlatformEventListenerTest {

    @Mock
    private DataPlatformClient dataPlatformClient;

    @InjectMocks
    private DataPlatformEventListener listener;

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 데이터 플랫폼에 전송")
    void onPaymentCompleted_전송성공() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L,
            new BigDecimal("50000"),
            List.of(new PaymentCompletedEvent.SeatInfo(1L, 1, new BigDecimal("50000")))
        );

        // when
        listener.onPaymentCompleted(event);

        // then
        verify(dataPlatformClient).sendReservationData(any(ReservationDataPayload.class));
    }

    @Test
    @DisplayName("데이터 플랫폼 전송 실패 시 예외가 전파되지 않음")
    void onPaymentCompleted_전송실패_예외미전파() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            1L, 1L, 1L, 1L, 1L,
            new BigDecimal("50000"),
            List.of()
        );
        doThrow(new RuntimeException("API 오류")).when(dataPlatformClient)
            .sendReservationData(any());

        // when & then - 예외가 전파되지 않음
        assertDoesNotThrow(() -> listener.onPaymentCompleted(event));
    }
}
```

#### 5.2 통합 테스트
**파일**: `test/payment/application/PaymentEventIntegrationTest.java`

```java
@SpringBootTest
@Transactional
class PaymentEventIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private DataPlatformClient dataPlatformClient;

    @Test
    @DisplayName("결제 완료 시 데이터 플랫폼 전송 이벤트 발행")
    void processPayment_이벤트발행() {
        // given
        // 테스트 데이터 설정...

        // when
        Payment payment = paymentService.processPayment(reservationId, userId);

        // then
        // 비동기 처리 대기
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(dataPlatformClient).sendReservationData(any())
            );
    }
}
```

---

## 파일 구조

```
server-java/src/main/java/kr/hhplus/be/server/
├── payment/
│   ├── application/
│   │   ├── PaymentService.java              (수정: 이벤트 발행 확장)
│   │   └── event/
│   │       └── DataPlatformEventListener.java (신규)
│   ├── domain/
│   │   └── event/
│   │       └── PaymentCompletedEvent.java    (수정: 필드 확장)
│   └── infrastructure/
│       └── external/
│           ├── DataPlatformClient.java       (신규: Mock API)
│           └── ReservationDataPayload.java   (신규: 전송 DTO)
└── config/
    └── async/
        └── AsyncConfig.java                  (신규: 비동기 설정)
```

---

## 이벤트 흐름도

```
┌─────────────────┐
│  PaymentService │
│ processPayment()│
└────────┬────────┘
         │ 트랜잭션 커밋
         ▼
┌─────────────────────────────┐
│   PaymentCompletedEvent     │
│   (확장된 예약 정보 포함)     │
└────────┬───────────┬────────┘
         │           │
         ▼           ▼
┌────────────────┐  ┌─────────────────────┐
│ ConcertRanking │  │ DataPlatformEvent   │
│ EventListener  │  │ Listener            │
│ (기존 - 동기)   │  │ (신규 - @Async)      │
└────────┬───────┘  └──────────┬──────────┘
         │                     │
         ▼                     ▼
┌────────────────┐  ┌─────────────────────┐
│ Redis 랭킹     │  │ DataPlatformClient  │
│ 업데이트       │  │ (Mock API 호출)      │
└────────────────┘  └─────────────────────┘
```

---

## 핵심 설계 결정 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 이벤트 전략 | 기존 이벤트 확장 | 하나의 비즈니스 이벤트, 여러 리스너 |
| 발행 시점 | AFTER_COMMIT | 트랜잭션 성공 시에만 |
| 처리 방식 | @Async 비동기 | 결제 응답 지연 방지 |
| 실패 처리 | try-catch로 격리 | 결제에 영향 없음 |

---

## 다음 단계

1. **Phase 1**: PaymentCompletedEvent 확장
2. **Phase 2**: DataPlatformClient 및 Payload 생성
3. **Phase 3**: DataPlatformEventListener 구현
4. **Phase 4**: AsyncConfig 설정
5. **Phase 5**: 테스트 작성
