# TDD 기반 콘서트 예약 시스템 구현 계획

## 📋 개발 전략

### 1️⃣ TDD 기반 개발 (테스트 먼저 작성)
- 각 기능마다 테스트 코드 먼저 작성 → 실패 확인 → 구현 → 테스트 통과

### 2️⃣ 단계적 아키텍처 전환
- **1단계**: 모든 기능을 레이어드 아키텍처로 구현
- **2단계**: 예약/결제 기능만 클린 아키텍처로 리팩토링

### 3️⃣ 도메인 설계 원칙
- **레이어드 아키텍처**: 도메인 객체에 JPA 어노테이션 직접 사용 (빠른 개발)
- **레이어드 Repository**: 인터페이스/구현체 분리 (연습 목적)
- **클린 아키텍처 전환 시**: 순수 도메인 객체 + JpaEntity 분리

### 4️⃣ 공통 모듈 전략
- **필요할 때 만들기**: 공통 예외, 응답 객체는 필요한 시점에 생성

---

## 🏗️ Phase 1: 레이어드 아키텍처 전체 구현

### 패키지 구조 (도메인 기준)
```
src/main/java/kr/hhplus/be/server/
├── common/                              (필요시 추가)
│   ├── exception/
│   │   ├── BusinessException
│   │   └── ErrorCode (enum)
│   └── response/
│       └── ApiResponse<T>
│
├── user/                                (User 도메인)
│   ├── domain/                          (도메인 모델)
│   │   ├── User
│   │   ├── UserBalance
│   │   ├── BalanceTransaction
│   │   └── repository/                  (Repository 인터페이스)
│   │       ├── UserRepository
│   │       ├── UserBalanceRepository
│   │       └── BalanceTransactionRepository
│   ├── application/                     (서비스 레이어)
│   │   └── UserBalanceService
│   ├── infrastructure/                  (Infrastructure 구현)
│   │   └── persistence/
│   │       ├── UserRepositoryImpl
│   │       ├── UserBalanceRepositoryImpl
│   │       ├── BalanceTransactionRepositoryImpl
│   │       └── UserBalanceJpaRepository  (JPA Repository)
│   └── presentation/                    (API 레이어)
│       ├── UserBalanceController
│       ├── request/
│       │   └── ChargeBalanceRequest
│       └── response/
│           └── UserBalanceResponse
│
├── concert/                             (Concert 도메인)
│   ├── domain/
│   │   ├── Concert
│   │   ├── ConcertSchedule
│   │   ├── ScheduleSeat
│   │   ├── enums/
│   │   │   ├── ScheduleStatus
│   │   │   └── SeatStatus
│   │   └── repository/
│   │       ├── ConcertRepository
│   │       ├── ConcertScheduleRepository
│   │       └── ScheduleSeatRepository
│   ├── application/
│   │   └── ConcertService
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── ConcertRepositoryImpl
│   │       ├── ConcertScheduleRepositoryImpl
│   │       ├── ScheduleSeatRepositoryImpl
│   │       └── ConcertJpaRepository
│   └── presentation/
│       ├── ConcertController
│       ├── request/
│       └── response/
│           ├── ConcertScheduleResponse
│           └── SeatResponse
│
├── reservation/                         (Reservation 도메인)
│   ├── domain/
│   │   ├── Reservation
│   │   ├── ReservationDetail
│   │   ├── enums/
│   │   │   └── ReservationStatus
│   │   └── repository/
│   │       ├── ReservationRepository
│   │       └── ReservationDetailRepository
│   ├── application/
│   │   └── ReservationService
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── ReservationRepositoryImpl
│   │       ├── ReservationDetailRepositoryImpl
│   │       └── ReservationJpaRepository
│   └── presentation/
│       ├── ReservationController
│       ├── request/
│       │   └── CreateReservationRequest
│       └── response/
│           └── ReservationResponse
│
├── payment/                             (Payment 도메인)
│   ├── domain/
│   │   ├── Payment
│   │   ├── enums/
│   │   │   └── PaymentStatus
│   │   └── repository/
│   │       └── PaymentRepository
│   ├── application/
│   │   └── PaymentService
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── PaymentRepositoryImpl
│   │       └── PaymentJpaRepository
│   └── presentation/
│       ├── PaymentController
│       ├── request/
│       │   └── ProcessPaymentRequest
│       └── response/
│           └── PaymentResponse
│
└── queue/                               (Queue 도메인)
    ├── domain/
    │   ├── QueueToken
    │   ├── enums/
    │   │   └── QueueStatus
    │   └── repository/
    │       └── QueueTokenRepository
    ├── application/
    │   └── QueueService
    ├── infrastructure/
    │   └── persistence/
    │       ├── QueueTokenRepositoryImpl
    │       └── QueueTokenJpaRepository
    └── presentation/
        ├── QueueController
        ├── request/
        │   └── IssueTokenRequest
        └── response/
            └── QueueTokenResponse
```

---

## 🚀 Step-by-Step 구현 순서

### Step 1: User 도메인 - 포인트 충전 기능 (TDD)

#### 1.1 도메인 엔티티 테스트 & 구현

**목표**: 도메인 객체의 비즈니스 로직을 TDD로 구현

**테스트 먼저 (Red)**:
```java
// test/user/domain/UserBalanceTest.java
class UserBalanceTest {

    @Test
    @DisplayName("잔액 충전 성공")
    void charge_정상금액_성공() {
        // given
        UserBalance balance = UserBalance.builder()
            .userId(1L)
            .currentBalance(new BigDecimal("5000"))
            .build();

        // when
        UserBalance charged = balance.charge(new BigDecimal("3000"));

        // then
        assertThat(charged.getCurrentBalance())
            .isEqualTo(new BigDecimal("8000"));
    }

    @Test
    @DisplayName("음수 충전 시 예외 발생")
    void charge_음수금액_예외() {
        // given
        UserBalance balance = UserBalance.builder()
            .userId(1L)
            .currentBalance(new BigDecimal("5000"))
            .build();

        // when & then
        assertThatThrownBy(() -> balance.charge(new BigDecimal("-1000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("충전 금액은 양수");
    }

    @Test
    @DisplayName("최대 충전 한도 초과 시 예외")
    void charge_한도초과_예외() {
        // given
        UserBalance balance = UserBalance.builder()
            .userId(1L)
            .currentBalance(new BigDecimal("5000"))
            .build();

        // when & then
        assertThatThrownBy(() -> balance.charge(new BigDecimal("1000000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("최대 충전 한도");
    }

    @Test
    @DisplayName("잔액 사용 성공")
    void use_정상금액_성공() {
        // given
        UserBalance balance = UserBalance.builder()
            .userId(1L)
            .currentBalance(new BigDecimal("5000"))
            .build();

        // when
        UserBalance used = balance.use(new BigDecimal("2000"));

        // then
        assertThat(used.getCurrentBalance())
            .isEqualTo(new BigDecimal("3000"));
    }

    @Test
    @DisplayName("잔액 부족 시 예외")
    void use_잔액부족_예외() {
        // given
        UserBalance balance = UserBalance.builder()
            .userId(1L)
            .currentBalance(new BigDecimal("1000"))
            .build();

        // when & then
        assertThatThrownBy(() -> balance.use(new BigDecimal("2000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("잔액이 부족");
    }
}
```

**구현 (Green)**:
```java
// user/domain/UserBalance.java
@Entity
@Table(name = "user_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private BigDecimal currentBalance;

    private BigDecimal totalCharged;

    private BigDecimal totalUsed;

    @Version
    private Integer version;  // Optimistic Lock

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 비즈니스 상수
    private static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("100");
    private static final BigDecimal MAX_BALANCE = new BigDecimal("10000000");

    @Builder
    public UserBalance(Long userId, BigDecimal currentBalance) {
        this.userId = userId;
        this.currentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        this.totalCharged = BigDecimal.ZERO;
        this.totalUsed = BigDecimal.ZERO;
    }

    // 비즈니스 로직: 충전
    public UserBalance charge(BigDecimal amount) {
        validateChargeAmount(amount);

        BigDecimal newBalance = this.currentBalance.add(amount);
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("최대 보유 한도를 초과할 수 없습니다.");
        }

        this.currentBalance = newBalance;
        this.totalCharged = this.totalCharged.add(amount);
        return this;
    }

    // 비즈니스 로직: 사용
    public UserBalance use(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 금액은 양수여야 합니다.");
        }

        if (this.currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalUsed = this.totalUsed.add(amount);
        return this;
    }

    private void validateChargeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 양수여야 합니다.");
        }

        if (amount.compareTo(MIN_CHARGE_AMOUNT) < 0) {
            throw new IllegalArgumentException("최소 충전 금액은 " + MIN_CHARGE_AMOUNT + "원입니다.");
        }

        if (amount.compareTo(MAX_CHARGE_AMOUNT) > 0) {
            throw new IllegalArgumentException("최대 충전 한도는 " + MAX_CHARGE_AMOUNT + "원입니다.");
        }
    }

    public static UserBalance create(Long userId) {
        return UserBalance.builder()
            .userId(userId)
            .currentBalance(BigDecimal.ZERO)
            .build();
    }
}
```

**리팩토링 (Refactor)**: 필요시 공통 예외 클래스 추출

---

#### 1.2 Repository 인터페이스 & 구현체

**인터페이스 정의**:
```java
// user/domain/repository/UserBalanceRepository.java
public interface UserBalanceRepository {
    Optional<UserBalance> findByUserId(Long userId);
    UserBalance save(UserBalance userBalance);
    Optional<UserBalance> findByUserIdWithLock(Long userId);
}
```

**JPA Repository**:
```java
// user/infrastructure/persistence/UserBalanceJpaRepository.java
public interface UserBalanceJpaRepository extends JpaRepository<UserBalance, Long> {

    Optional<UserBalance> findByUserId(Long userId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ub FROM UserBalance ub WHERE ub.userId = :userId")
    Optional<UserBalance> findByUserIdWithLock(@Param("userId") Long userId);
}
```

**구현체**:
```java
// user/infrastructure/persistence/UserBalanceRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class UserBalanceRepositoryImpl implements UserBalanceRepository {

    private final UserBalanceJpaRepository jpaRepository;

    @Override
    public Optional<UserBalance> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public UserBalance save(UserBalance userBalance) {
        return jpaRepository.save(userBalance);
    }

    @Override
    public Optional<UserBalance> findByUserIdWithLock(Long userId) {
        return jpaRepository.findByUserIdWithLock(userId);
    }
}
```

---

#### 1.3 Service 레이어 테스트 & 구현

**테스트 먼저 (Red)**:
```java
// test/user/application/UserBalanceServiceTest.java
@ExtendWith(MockitoExtension.class)
class UserBalanceServiceTest {

    @Mock
    private UserBalanceRepository userBalanceRepository;

    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;

    @InjectMocks
    private UserBalanceService userBalanceService;

    @Test
    @DisplayName("포인트 충전 성공")
    void charge_정상금액_성공() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("10000");
        UserBalance existingBalance = UserBalance.builder()
            .userId(userId)
            .currentBalance(new BigDecimal("5000"))
            .build();

        when(userBalanceRepository.findByUserId(userId))
            .thenReturn(Optional.of(existingBalance));
        when(userBalanceRepository.save(any(UserBalance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserBalance result = userBalanceService.charge(userId, chargeAmount);

        // then
        assertThat(result.getCurrentBalance())
            .isEqualTo(new BigDecimal("15000"));

        // Repository 호출 검증
        verify(userBalanceRepository).findByUserId(userId);
        verify(userBalanceRepository).save(any(UserBalance.class));
        verify(balanceTransactionRepository).save(any(BalanceTransaction.class));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없을 때 예외")
    void charge_사용자없음_예외() {
        // given
        Long userId = 999L;
        BigDecimal chargeAmount = new BigDecimal("10000");

        when(userBalanceRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userBalanceService.charge(userId, chargeAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("잔액 조회 성공")
    void getBalance_성공() {
        // given
        Long userId = 1L;
        UserBalance balance = UserBalance.builder()
            .userId(userId)
            .currentBalance(new BigDecimal("5000"))
            .build();

        when(userBalanceRepository.findByUserId(userId))
            .thenReturn(Optional.of(balance));

        // when
        UserBalance result = userBalanceService.getBalance(userId);

        // then
        assertThat(result.getCurrentBalance()).isEqualTo(new BigDecimal("5000"));
        verify(userBalanceRepository).findByUserId(userId);
    }
}
```

**구현 (Green)**:
```java
// user/application/UserBalanceService.java
@Service
@Transactional
@RequiredArgsConstructor
public class UserBalanceService {

    private final UserBalanceRepository userBalanceRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;

    public UserBalance charge(Long userId, BigDecimal amount) {
        // 1. 사용자 잔액 조회
        UserBalance balance = userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 충전 (도메인 로직 위임)
        balance.charge(amount);

        // 3. 트랜잭션 기록
        BalanceTransaction transaction = BalanceTransaction.charge(
            userId,
            amount,
            balance.getCurrentBalance()
        );
        balanceTransactionRepository.save(transaction);

        // 4. 저장
        return userBalanceRepository.save(balance);
    }

    @Transactional(readOnly = true)
    public UserBalance getBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
```

---

### Step 2: Concert 도메인 - 콘서트 조회 기능 (TDD)

> **구현 범위**: Concert, ConcertSchedule, ScheduleSeat 엔티티 + 조회 기능
>
> **ERD 참고**:
> - `concerts` (콘서트 기본 정보)
> - `concert_schedules` (콘서트 일정, FK: concert_id, venue_id)
> - `schedule_seats` (일정별 좌석, FK: schedule_id, venue_seat_id)
> - `venues`, `venue_seats`는 이번 단계에서 **미구현** (FK 참조만 사용)

#### 2.1 Enum 클래스 구현

**먼저 Enum 타입 정의**:
```java
// concert/domain/enums/ScheduleStatus.java
public enum ScheduleStatus {
    AVAILABLE,      // 예약 가능
    SOLD_OUT,       // 매진
    CANCELLED       // 취소됨
}

// concert/domain/enums/SeatStatus.java
public enum SeatStatus {
    AVAILABLE,      // 예약 가능
    RESERVED,       // 임시 예약 (10분)
    SOLD            // 결제 완료
}
```

---

#### 2.2 도메인 엔티티 테스트 & 구현

##### 2.2.1 Concert 엔티티

**테스트**:
```java
// test/concert/domain/ConcertTest.java
class ConcertTest {

    @Test
    @DisplayName("콘서트 생성 성공")
    void create_성공() {
        // given & when
        Concert concert = Concert.builder()
            .title("아이유 콘서트")
            .artist("아이유")
            .durationMinutes(120)
            .build();

        // then
        assertThat(concert.getTitle()).isEqualTo("아이유 콘서트");
        assertThat(concert.getArtist()).isEqualTo("아이유");
        assertThat(concert.getDurationMinutes()).isEqualTo(120);
    }
}
```

**구현**:
```java
// concert/domain/Concert.java
@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String artist;
    private String description;
    private String posterImageUrl;
    private Integer durationMinutes;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Concert(String title, String artist, String description,
                   String posterImageUrl, Integer durationMinutes) {
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.posterImageUrl = posterImageUrl;
        this.durationMinutes = durationMinutes;
    }
}
```

---

##### 2.2.2 ConcertSchedule 엔티티

**테스트**:
```java
// test/concert/domain/ConcertScheduleTest.java
class ConcertScheduleTest {

    @Test
    @DisplayName("예약 가능 기간인지 확인")
    void isBookingOpen_예약가능기간_true() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(1))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("예약 오픈 전")
    void isBookingOpen_오픈전_false() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().plusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(2))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("예약 마감 후")
    void isBookingOpen_마감후_false() {
        // given
        ConcertSchedule schedule = ConcertSchedule.builder()
            .bookingOpenAt(LocalDateTime.now().minusDays(2))
            .bookingCloseAt(LocalDateTime.now().minusHours(1))
            .build();

        // when
        boolean result = schedule.isBookingOpen();

        // then
        assertThat(result).isFalse();
    }
}
```

**구현**:
```java
// concert/domain/ConcertSchedule.java
@Entity
@Table(name = "concert_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long concertId;
    private Long venueId;

    private LocalDate performanceDate;
    private LocalTime performanceTime;

    private LocalDateTime bookingOpenAt;
    private LocalDateTime bookingCloseAt;

    private Integer maxSeatsPerUser;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    // 비즈니스 로직: 예약 가능 기간 체크
    public boolean isBookingOpen() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(bookingOpenAt) && now.isBefore(bookingCloseAt)
               && status == ScheduleStatus.AVAILABLE;
    }

    @Builder
    public ConcertSchedule(Long concertId, Long venueId,
                          LocalDate performanceDate, LocalTime performanceTime,
                          LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt,
                          Integer maxSeatsPerUser) {
        this.concertId = concertId;
        this.venueId = venueId;
        this.performanceDate = performanceDate;
        this.performanceTime = performanceTime;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.maxSeatsPerUser = maxSeatsPerUser != null ? maxSeatsPerUser : 4;
        this.status = ScheduleStatus.AVAILABLE;
    }
}
```

---

##### 2.2.3 ScheduleSeat 엔티티 (간단 버전)

**테스트**:
```java
// test/concert/domain/ScheduleSeatTest.java
class ScheduleSeatTest {

    @Test
    @DisplayName("좌석 생성 시 기본 상태는 AVAILABLE")
    void create_기본상태_AVAILABLE() {
        // given & when
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .build();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("좌석 예약 가능 여부 체크")
    void isAvailable_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(1L)
            .price(new BigDecimal("50000"))
            .build();

        // when
        boolean result = seat.isAvailable();

        // then
        assertThat(result).isTrue();
    }
}
```

**구현**:
```java
// concert/domain/ScheduleSeat.java
@Entity
@Table(name = "schedule_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scheduleId;
    private Long venueSeatId;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private LocalDateTime reservedUntil;

    @Version
    private Integer version;  // Optimistic Lock

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 비즈니스 로직: 예약 가능 여부
    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    @Builder
    public ScheduleSeat(Long scheduleId, Long venueSeatId, BigDecimal price) {
        this.scheduleId = scheduleId;
        this.venueSeatId = venueSeatId;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }
}
```

---

#### 2.3 Repository 인터페이스 & 구현체

**인터페이스 정의**:
```java
// concert/domain/repository/ConcertRepository.java
public interface ConcertRepository {
    Optional<Concert> findById(Long id);
    List<Concert> findAll();
}

// concert/domain/repository/ConcertScheduleRepository.java
public interface ConcertScheduleRepository {
    Optional<ConcertSchedule> findById(Long id);
    List<ConcertSchedule> findByConcertId(Long concertId);
    List<ConcertSchedule> findByConcertIdAndDateRange(
        Long concertId, LocalDate fromDate, LocalDate toDate
    );
}

// concert/domain/repository/ScheduleSeatRepository.java
public interface ScheduleSeatRepository {
    List<ScheduleSeat> findByScheduleId(Long scheduleId);
    Optional<ScheduleSeat> findById(Long id);
    List<ScheduleSeat> findAllByIdWithLock(List<Long> ids);
}
```

**JPA Repository**:
```java
// concert/infrastructure/persistence/ConcertJpaRepository.java
public interface ConcertJpaRepository extends JpaRepository<Concert, Long> {
}

// concert/infrastructure/persistence/ConcertScheduleJpaRepository.java
public interface ConcertScheduleJpaRepository extends JpaRepository<ConcertSchedule, Long> {

    List<ConcertSchedule> findByConcertId(Long concertId);

    @Query("SELECT cs FROM ConcertSchedule cs " +
           "WHERE cs.concertId = :concertId " +
           "AND cs.performanceDate BETWEEN :fromDate AND :toDate " +
           "ORDER BY cs.performanceDate, cs.performanceTime")
    List<ConcertSchedule> findByConcertIdAndDateRange(
        @Param("concertId") Long concertId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );
}

// concert/infrastructure/persistence/ScheduleSeatJpaRepository.java
public interface ScheduleSeatJpaRepository extends JpaRepository<ScheduleSeat, Long> {

    List<ScheduleSeat> findByScheduleId(Long scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ScheduleSeat ss WHERE ss.id IN :ids")
    List<ScheduleSeat> findAllByIdWithLock(@Param("ids") List<Long> ids);
}
```

**구현체**:
```java
// concert/infrastructure/persistence/ConcertRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class ConcertRepositoryImpl implements ConcertRepository {

    private final ConcertJpaRepository jpaRepository;

    @Override
    public Optional<Concert> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Concert> findAll() {
        return jpaRepository.findAll();
    }
}

// concert/infrastructure/persistence/ConcertScheduleRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class ConcertScheduleRepositoryImpl implements ConcertScheduleRepository {

    private final ConcertScheduleJpaRepository jpaRepository;

    @Override
    public Optional<ConcertSchedule> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ConcertSchedule> findByConcertId(Long concertId) {
        return jpaRepository.findByConcertId(concertId);
    }

    @Override
    public List<ConcertSchedule> findByConcertIdAndDateRange(
        Long concertId, LocalDate fromDate, LocalDate toDate
    ) {
        return jpaRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate);
    }
}

// concert/infrastructure/persistence/ScheduleSeatRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class ScheduleSeatRepositoryImpl implements ScheduleSeatRepository {

    private final ScheduleSeatJpaRepository jpaRepository;

    @Override
    public List<ScheduleSeat> findByScheduleId(Long scheduleId) {
        return jpaRepository.findByScheduleId(scheduleId);
    }

    @Override
    public Optional<ScheduleSeat> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ScheduleSeat> findAllByIdWithLock(List<Long> ids) {
        return jpaRepository.findAllByIdWithLock(ids);
    }
}
```

---

#### 2.4 Service 레이어 테스트 & 구현

**테스트 먼저**:
```java
@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertScheduleRepository scheduleRepository;

    @Mock
    private ScheduleSeatRepository seatRepository;

    @InjectMocks
    private ConcertService concertService;

    @Test
    @DisplayName("예약 가능한 일정만 조회")
    void getAvailableSchedules_예약가능_필터링() {
        // given
        Long concertId = 1L;
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = fromDate.plusMonths(1);

        List<ConcertSchedule> mockSchedules = List.of(
            createSchedule(1L, fromDate, true),  // 예약 가능
            createSchedule(2L, fromDate.plusDays(1), false)  // 예약 불가
        );

        when(scheduleRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate))
            .thenReturn(mockSchedules);

        // when
        List<ConcertScheduleResponse> result =
            concertService.getAvailableSchedules(concertId, fromDate, toDate);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScheduleId()).isEqualTo(1L);
    }

    private ConcertSchedule createSchedule(Long id, LocalDate date, boolean isOpen) {
        return ConcertSchedule.builder()
            .concertId(1L)
            .performanceDate(date)
            .bookingOpenAt(isOpen ? LocalDateTime.now().minusHours(1) : LocalDateTime.now().plusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(1))
            .build();
    }
}
```

**구현**:
```java
// concert/application/ConcertService.java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository seatRepository;

    public List<ConcertScheduleResponse> getAvailableSchedules(
        Long concertId, LocalDate fromDate, LocalDate toDate
    ) {
        return scheduleRepository
            .findByConcertIdAndDateRange(concertId, fromDate, toDate)
            .stream()
            .filter(ConcertSchedule::isBookingOpen)  // 도메인 로직 활용
            .map(ConcertScheduleResponse::from)
            .toList();
    }

    public List<SeatResponse> getAvailableSeats(Long scheduleId) {
        return seatRepository.findByScheduleId(scheduleId)
            .stream()
            .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
            .map(SeatResponse::from)
            .toList();
    }
}
```

---

### Step 3: Reservation 도메인 - 좌석 예약 기능 (TDD)

#### 3.1 도메인 엔티티 테스트 & 구현

**ScheduleSeat 테스트**:
```java
// test/concert/domain/ScheduleSeatTest.java
class ScheduleSeatTest {

    @Test
    @DisplayName("좌석 예약 성공")
    void reserve_예약가능_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .status(SeatStatus.AVAILABLE)
            .build();

        // when
        seat.reserve();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("이미 예약된 좌석 예외")
    void reserve_이미예약됨_예외() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .status(SeatStatus.RESERVED)
            .build();

        // when & then
        assertThatThrownBy(() -> seat.reserve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("예약 불가능");
    }

    @Test
    @DisplayName("좌석 확정 (SOLD)")
    void confirm_성공() {
        // given
        ScheduleSeat seat = ScheduleSeat.builder()
            .status(SeatStatus.RESERVED)
            .build();

        // when
        seat.confirm();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }
}
```

**Reservation 테스트**:
```java
// test/reservation/domain/ReservationTest.java
class ReservationTest {

    @Test
    @DisplayName("예약 생성 성공")
    void create_성공() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<ScheduleSeat> seats = createSeats(2);

        // when
        Reservation reservation = Reservation.create(userId, scheduleId, seats);

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getTotalAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(reservation.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("예약 확정 성공")
    void confirm_PENDING상태_성공() {
        // given
        Reservation reservation = Reservation.create(1L, 1L, createSeats(1));

        // when
        reservation.confirm();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("만료된 예약은 확정 불가")
    void confirm_만료됨_예외() {
        // given
        Reservation reservation = Reservation.builder()
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().minusMinutes(1))  // 만료됨
            .build();

        // when & then
        assertThatThrownBy(() -> reservation.confirm())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("만료");
    }
}
```

#### 3.2 Service 레이어 테스트 & 구현

**테스트**:
```java
// test/reservation/application/ReservationServiceTest.java
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ScheduleSeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("좌석 예약 성공")
    void createReservation_성공() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(1L, 2L);

        List<ScheduleSeat> availableSeats = List.of(
            createSeat(1L, SeatStatus.AVAILABLE),
            createSeat(2L, SeatStatus.AVAILABLE)
        );

        when(seatRepository.findAllByIdWithLock(seatIds))
            .thenReturn(availableSeats);
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Reservation result = reservationService.createReservation(userId, scheduleId, seatIds);

        // then
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);

        // 좌석 상태 변경 확인
        verify(seatRepository).saveAll(argThat(seats ->
            seats.stream().allMatch(s -> s.getStatus() == SeatStatus.RESERVED)
        ));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("이미 예약된 좌석 포함 시 예외")
    void createReservation_이미예약됨_예외() {
        // given
        List<Long> seatIds = List.of(1L, 2L);
        List<ScheduleSeat> seats = List.of(
            createSeat(1L, SeatStatus.AVAILABLE),
            createSeat(2L, SeatStatus.RESERVED)  // 이미 예약됨
        );

        when(seatRepository.findAllByIdWithLock(seatIds))
            .thenReturn(seats);

        // when & then
        assertThatThrownBy(() ->
            reservationService.createReservation(1L, 1L, seatIds)
        ).isInstanceOf(IllegalStateException.class);
    }
}
```

**구현**:
```java
// reservation/application/ReservationService.java
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ScheduleSeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    public Reservation createReservation(Long userId, Long scheduleId, List<Long> seatIds) {
        // 1. 좌석 조회 (비관적 락)
        List<ScheduleSeat> seats = seatRepository.findAllByIdWithLock(seatIds);

        if (seats.size() != seatIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 좌석이 포함되어 있습니다.");
        }

        // 2. 좌석 예약 (도메인 로직)
        seats.forEach(ScheduleSeat::reserve);
        seatRepository.saveAll(seats);

        // 3. 예약 생성
        Reservation reservation = Reservation.create(userId, scheduleId, seats);

        return reservationRepository.save(reservation);
    }
}
```

---

### Step 4: Payment 도메인 - 결제 기능 (TDD)

#### 4.1 Service 레이어 테스트 & 구현

**테스트**:
```java
// test/payment/application/PaymentServiceTest.java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserBalanceRepository userBalanceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ScheduleSeatRepository seatRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("결제 성공")
    void processPayment_성공() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = createPendingReservation(userId, new BigDecimal("50000"));
        UserBalance balance = createBalance(userId, new BigDecimal("100000"));

        when(reservationRepository.findById(reservationId))
            .thenReturn(Optional.of(reservation));
        when(userBalanceRepository.findByUserIdWithLock(userId))
            .thenReturn(Optional.of(balance));

        // when
        Payment result = paymentService.processPayment(reservationId, userId);

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(balance.getCurrentBalance()).isEqualTo(new BigDecimal("50000"));

        // 예약 상태 확정 확인
        verify(reservationRepository).save(argThat(r ->
            r.getStatus() == ReservationStatus.CONFIRMED
        ));

        // 좌석 상태 확정 확인
        verify(seatRepository).saveAll(any());
    }

    @Test
    @DisplayName("잔액 부족 시 예외")
    void processPayment_잔액부족_예외() {
        // given
        Reservation reservation = createPendingReservation(1L, new BigDecimal("100000"));
        UserBalance balance = createBalance(1L, new BigDecimal("50000"));  // 부족

        when(reservationRepository.findById(1L))
            .thenReturn(Optional.of(reservation));
        when(userBalanceRepository.findByUserIdWithLock(1L))
            .thenReturn(Optional.of(balance));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(1L, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("잔액이 부족");
    }
}
```

**구현**:
```java
// payment/application/PaymentService.java
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationRepository reservationRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final PaymentRepository paymentRepository;
    private final ScheduleSeatRepository seatRepository;

    public Payment processPayment(Long reservationId, Long userId) {
        // 1. 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 2. 잔액 조회 및 차감
        UserBalance balance = userBalanceRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        balance.use(reservation.getTotalAmount());
        userBalanceRepository.save(balance);

        // 3. 예약 확정
        reservation.confirm();
        reservationRepository.save(reservation);

        // 4. 좌석 확정
        List<ScheduleSeat> seats = seatRepository.findByReservationId(reservationId);
        seats.forEach(ScheduleSeat::confirm);
        seatRepository.saveAll(seats);

        // 5. 결제 기록
        Payment payment = Payment.complete(reservation, balance.getCurrentBalance());
        return paymentRepository.save(payment);
    }
}
```

---

## 🔄 Phase 2: 클린 아키텍처로 점진적 리팩토링

> **참고**: 이 섹션은 과제 가이드의 "클린 아키텍처 실무 적용" 패턴을 따릅니다.

### Step 5: 예약/결제 도메인을 클린 아키텍처로 전환

#### 5.1 순수 도메인 객체로 분리 (JPA 의존성 제거)

**목표**: 도메인 모델을 프레임워크(JPA)로부터 완전히 독립시키기

**변경 전 (레이어드)**:
```java
@Entity  // ← JPA 의존
@Table(name = "reservations")
public class Reservation {
    @Id @GeneratedValue
    private Long id;
    private ReservationStatus status;

    protected Reservation() {}  // JPA 기본 생성자
}
```

**변경 후 (클린 아키텍처) - 순수 도메인**:
```java
// reservation/domain/Reservation.java (JPA 어노테이션 완전 제거)
public class Reservation {
    private Long id;
    private final Long userId;
    private final Long scheduleId;
    private final Money totalAmount;
    private final ReservationStatus status;
    private final LocalDateTime expiresAt;

    // private 생성자 (외부 생성 제한)
    private Reservation(Long id, Long userId, Long scheduleId,
                       Money totalAmount, ReservationStatus status,
                       LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.scheduleId = scheduleId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    // 팩토리 메서드: 예약 생성
    public static Reservation create(Long userId, Long scheduleId, List<Seat> seats) {
        Money total = seats.stream()
            .map(Seat::getPrice)
            .reduce(Money.ZERO, Money::add);

        return new Reservation(
            null,  // ID는 나중에 할당
            userId,
            scheduleId,
            total,
            ReservationStatus.PENDING,
            LocalDateTime.now().plusMinutes(10)
        );
    }

    // 비즈니스 로직: 예약 확정
    public Reservation confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("예약 확정 불가");
        }
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new IllegalStateException("예약 만료");
        }
        // 불변 객체 - 새 인스턴스 반환
        return new Reservation(id, userId, scheduleId, totalAmount,
                              ReservationStatus.CONFIRMED, expiresAt);
    }

    // Infrastructure에서만 호출 (ID 할당용)
    void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID가 이미 할당되었습니다.");
        }
        this.id = id;
    }

    // Getters
    public Long getId() { return id; }
    public Money getTotalAmount() { return totalAmount; }
    public ReservationStatus getStatus() { return status; }
    // ...
}
```

**도메인 테스트 (Spring 의존성 없음)**:
```java
// 순수 JUnit 테스트 - @SpringBootTest 불필요
class ReservationDomainTest {

    @Test
    void 예약_생성_성공() {
        // given
        List<Seat> seats = List.of(
            new Seat(1L, new Money(50000)),
            new Seat(2L, new Money(50000))
        );

        // when
        Reservation reservation = Reservation.create(1L, 1L, seats);

        // then
        assertThat(reservation.getTotalAmount()).isEqualTo(new Money(100000));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getId()).isNull();  // 아직 ID 할당 안됨
    }

    @Test
    void 예약_확정시_불변객체_반환() {
        // given
        Reservation original = Reservation.create(1L, 1L, createSeats());

        // when
        Reservation confirmed = original.confirm();

        // then
        assertThat(confirmed).isNotSameAs(original);  // 다른 인스턴스
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }
}
```

---

#### 5.2 JPA 엔티티 분리 (Infrastructure 전용 DTO)

**목표**: ORM 매핑을 Infrastructure 계층으로 격리

```java
// reservation/infrastructure/persistence/ReservationJpaEntity.java
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ReservationJpaEntity {  // package-private (외부 노출 금지)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long scheduleId;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    @Builder
    public ReservationJpaEntity(Long userId, Long scheduleId, BigDecimal totalAmount,
                                ReservationStatus status, LocalDateTime expiresAt) {
        this.userId = userId;
        this.scheduleId = scheduleId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    // Domain → Entity 변환 (Infrastructure 책임)
    public static ReservationJpaEntity from(Reservation domain) {
        return ReservationJpaEntity.builder()
            .userId(domain.getUserId())
            .scheduleId(domain.getScheduleId())
            .totalAmount(domain.getTotalAmount().getValue())
            .status(domain.getStatus())
            .expiresAt(domain.getExpiresAt())
            .build();
    }

    // Entity → Domain 변환
    public Reservation toDomain() {
        Reservation reservation = Reservation.create(
            this.userId,
            this.scheduleId,
            List.of()  // 좌석 정보는 별도 조회
        );
        reservation.assignId(this.id);  // ID 할당
        return reservation;
    }

    // 도메인 객체로 업데이트
    public void updateFrom(Reservation domain) {
        this.status = domain.getStatus();
        // 필요한 필드만 업데이트
    }
}
```

---

#### 5.3 Port 정의 (도메인 인터페이스)

**목표**: 도메인이 외부 세계와 통신하는 추상 포트 정의

```java
// reservation/application/port/out/LoadSeatPort.java
public interface LoadSeatPort {
    List<Seat> loadAvailableSeats(List<Long> seatIds);
}

// application/reservation/port/out/UpdateSeatPort.java
public interface UpdateSeatPort {
    void reserveSeats(List<Seat> seats);
    void confirmSeats(List<Seat> seats);
}

// application/reservation/port/out/SaveReservationPort.java
public interface SaveReservationPort {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(Long id);
}

// application/reservation/port/in/CreateReservationCommand.java
public record CreateReservationCommand(
    Long userId,
    Long scheduleId,
    List<Long> seatIds
) {}

// application/reservation/port/in/ReservationResult.java
public record ReservationResult(
    Long reservationId,
    Money totalAmount,
    ReservationStatus status,
    LocalDateTime expiresAt
) {
    public static ReservationResult from(Reservation reservation) {
        return new ReservationResult(
            reservation.getId(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getExpiresAt()
        );
    }
}
```

---

#### 5.4 Use Case 구현 (Application Layer)

**Use Case 인터페이스**:
```java
// application/reservation/port/in/CreateReservationUseCase.java
public interface CreateReservationUseCase {
    ReservationResult createReservation(CreateReservationCommand command);
}
```

**Use Case 테스트 (Port를 Mock으로)**:
```java
@ExtendWith(MockitoExtension.class)
class CreateReservationServiceTest {

    @Mock
    private LoadSeatPort loadSeatPort;

    @Mock
    private UpdateSeatPort updateSeatPort;

    @Mock
    private SaveReservationPort saveReservationPort;

    @InjectMocks
    private CreateReservationService useCase;

    @Test
    void 예약_생성_성공() {
        // given
        CreateReservationCommand command =
            new CreateReservationCommand(1L, 1L, List.of(1L, 2L));

        List<Seat> availableSeats = List.of(
            new Seat(1L, new Money(50000), SeatStatus.AVAILABLE),
            new Seat(2L, new Money(50000), SeatStatus.AVAILABLE)
        );

        when(loadSeatPort.loadAvailableSeats(command.seatIds()))
            .thenReturn(availableSeats);

        Reservation savedReservation = Reservation.create(1L, 1L, availableSeats);
        savedReservation.assignId(1L);

        when(saveReservationPort.save(any(Reservation.class)))
            .thenReturn(savedReservation);

        // when
        ReservationResult result = useCase.createReservation(command);

        // then
        assertThat(result.totalAmount()).isEqualTo(new Money(100000));
        assertThat(result.reservationId()).isEqualTo(1L);

        // Port 호출 검증 (순서 포함)
        InOrder inOrder = inOrder(loadSeatPort, updateSeatPort, saveReservationPort);
        inOrder.verify(loadSeatPort).loadAvailableSeats(command.seatIds());
        inOrder.verify(updateSeatPort).reserveSeats(availableSeats);
        inOrder.verify(saveReservationPort).save(any(Reservation.class));
    }

    @Test
    void 좌석_없을때_예외() {
        // given
        when(loadSeatPort.loadAvailableSeats(any()))
            .thenReturn(List.of());

        // when & then
        assertThatThrownBy(() ->
            useCase.createReservation(new CreateReservationCommand(1L, 1L, List.of(1L)))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
```

**Use Case 구현**:
```java
// application/reservation/CreateReservationService.java
@Service  // 또는 @Component (의존성 주입용)
@Transactional
@RequiredArgsConstructor
public class CreateReservationService implements CreateReservationUseCase {

    private final LoadSeatPort loadSeatPort;
    private final UpdateSeatPort updateSeatPort;
    private final SaveReservationPort saveReservationPort;

    @Override
    public ReservationResult createReservation(CreateReservationCommand command) {
        // 1. 좌석 조회 (Port 호출)
        List<Seat> seats = loadSeatPort.loadAvailableSeats(command.seatIds());

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("예약 가능한 좌석이 없습니다.");
        }

        // 2. 예약 생성 (순수 도메인 로직)
        Reservation reservation = Reservation.create(
            command.userId(),
            command.scheduleId(),
            seats
        );

        // 3. 좌석 예약 처리 (Port 호출)
        updateSeatPort.reserveSeats(seats);

        // 4. 예약 저장 (Port 호출)
        Reservation saved = saveReservationPort.save(reservation);

        return ReservationResult.from(saved);
    }
}
```

---

#### 5.5 Adapter 구현 (Infrastructure Layer)

**Repository Adapter**:
```java
// infrastructure/persistence/reservation/ReservationPersistenceAdapter.java
@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements SaveReservationPort {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        // 1. 도메인 → JPA 엔티티 변환
        ReservationJpaEntity entity = ReservationJpaEntity.from(reservation);

        // 2. JPA 저장
        ReservationJpaEntity saved = jpaRepository.save(entity);

        // 3. JPA 엔티티 → 도메인 변환
        Reservation savedDomain = saved.toDomain();

        return savedDomain;
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id)
            .map(ReservationJpaEntity::toDomain);
    }
}
```

**Seat Port Adapter**:
```java
// infrastructure/persistence/reservation/SeatPersistenceAdapter.java
@Component
@RequiredArgsConstructor
public class SeatPersistenceAdapter implements LoadSeatPort, UpdateSeatPort {

    private final ScheduleSeatJpaRepository jpaRepository;

    @Override
    public List<Seat> loadAvailableSeats(List<Long> seatIds) {
        return jpaRepository.findAllByIdWithLock(seatIds)
            .stream()
            .map(SeatJpaEntity::toDomain)  // Entity → Domain 변환
            .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
            .toList();
    }

    @Override
    public void reserveSeats(List<Seat> seats) {
        // 도메인 로직 호출 후 저장
        seats.forEach(Seat::reserve);

        List<SeatJpaEntity> entities = seats.stream()
            .map(SeatJpaEntity::from)  // Domain → Entity 변환
            .toList();

        jpaRepository.saveAll(entities);
    }

    @Override
    public void confirmSeats(List<Seat> seats) {
        seats.forEach(Seat::confirm);

        List<SeatJpaEntity> entities = seats.stream()
            .map(SeatJpaEntity::from)
            .toList();

        jpaRepository.saveAll(entities);
    }
}
```

---

#### 5.6 Controller 변경 (최소한의 수정)

```java
// presentation/api/reservation/ReservationController.java
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final CreateReservationUseCase createReservationUseCase;  // 변경

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
        @RequestBody CreateReservationRequest request
    ) {
        // Request → Command 변환
        CreateReservationCommand command = new CreateReservationCommand(
            request.userId(),
            request.scheduleId(),
            request.seatIds()
        );

        // Use Case 호출
        ReservationResult result = createReservationUseCase.createReservation(command);

        // Result → Response 변환
        return ResponseEntity.ok(ReservationResponse.from(result));
    }
}
```

---

## 📊 TDD 사이클 요약

각 기능마다 다음 순서로 진행:

1. **Red**: 테스트 작성 → 실패 확인
2. **Green**: 최소한의 코드로 테스트 통과
3. **Refactor**: 코드 개선 (테스트는 계속 통과)

---

## 💡 핵심 원칙

1. **테스트 먼저**: 모든 코드는 테스트를 먼저 작성
2. **도메인 중심**: 비즈니스 로직은 도메인 객체에 위치
3. **Mock 활용**: 단위 테스트에서 외부 의존성은 Mock 처리
4. **점진적 리팩토링**: 레이어드 → 클린 아키텍처로 단계적 전환
5. **Repository 분리**: 인터페이스/구현체 분리로 DIP 실천

---

## 🚀 구현 순서 요약

### Phase 1: 레이어드 아키텍처
1. **User Domain - 포인트 충전** (Step 1)
   - UserBalance 도메인 엔티티 TDD
   - Repository 인터페이스/구현체
   - UserBalanceService TDD

2. **Concert Domain - 콘서트 조회** (Step 2)
   - ConcertSchedule 도메인 엔티티 TDD
   - Repository 인터페이스/구현체
   - ConcertService TDD

3. **Reservation Domain - 좌석 예약** (Step 3)
   - ScheduleSeat, Reservation 도메인 엔티티 TDD
   - Repository 인터페이스/구현체
   - ReservationService TDD

4. **Payment Domain - 결제** (Step 4)
   - Payment 도메인 엔티티 TDD
   - PaymentService TDD

### Phase 2: 클린 아키텍처 전환 (Step 5)
1. 순수 도메인 객체로 분리 (JPA 의존성 제거)
2. JPA 엔티티를 Infrastructure 전용 DTO로 분리
3. Port 인터페이스 정의 (In/Out)
4. Use Case 구현 (Port 의존)
5. Adapter 구현 (Port 구현체, 변환 로직 포함)
6. Controller 수정 (Use Case 호출)

---

## 📌 클린 아키텍처 전환의 핵심 차이점

| 항목 | 레이어드 (Phase 1) | 클린 아키텍처 (Phase 2) |
|------|------------------|---------------------|
| **도메인 객체** | `@Entity` JPA 의존 | 순수 POJO, 프레임워크 무관 |
| **ID 할당** | JPA가 자동 처리 | `assignId()` 메서드로 명시적 할당 |
| **변환 로직** | 불필요 (JPA 직접 사용) | Infrastructure에서 `toEntity()`, `toDomain()` |
| **Repository** | JPA Repository 직접 사용 | Port 인터페이스 → Adapter 구현 |
| **테스트** | Mock Repository | Mock Port (더 가벼움) |
| **Service 명칭** | `ReservationService` | `CreateReservationUseCase` (유스케이스 명확) |
| **의존성 방향** | Service → JPA Repo | UseCase → Port ← Adapter |

---

## 🎓 참고: 가이드 핵심 내용 정리

### 클린 아키텍처 적용 시 얻는 이점

1. **도메인 순수성**: 비즈니스 로직이 프레임워크(JPA, Spring)로부터 독립
2. **테스트 용이성**: Spring 컨텍스트 없이 순수 JUnit 테스트 가능
3. **기술 교체 비용 감소**: DB 변경 시 도메인 코드는 수정 불필요
4. **명확한 책임 분리**:
   - Domain: 비즈니스 규칙
   - Application: 유스케이스 오케스트레이션
   - Infrastructure: 기술 구현 세부사항

### 실무 적용 팁

- 처음부터 모든 것을 클린 아키텍처로 작성하지 않기
- **우선 Repository 의존성 분리** 부터 시작
- **도메인 로직과 기술적 구현을 구분**하는 것만으로도 큰 효과
- 복잡한 비즈니스 로직이 있는 핵심 도메인부터 적용
