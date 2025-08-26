# 동시성 제어 방식에 대한 분석 및 보고서

## 동시성 제어란? 
여러 스레드나 프로세스가 동시에 같은 자원(**데이터**)에 접근할 때 발생할 수 있는 문제를 방지하는 기법
### 포인트 시스템에서 발생할 수 있는 동시성 문제
- 예시 시나리오: 
  초기 잔액: 1000원

  Thread A: 500원 충전 → 1000 + 500 = 1500원
  Thread B: 300원 사용 → 1000 - 300 = 700원

  동시 실행 시:
  - 예상: 1000 + 500 - 300 = 1200원
  - 실제: 700원 또는 1500원 (Lost Update 발생)

## 동시성 제어 방법

### 1. synchronized (애플리케이션 레벨 락)
- **원리**: JVM 레벨에서 제공하는 가장 기본적인 동기화 메커니즘으로, 한 번에 하나의 스레드만 임계 영역에 접근 가능
- **장점**:
    - 구현이 매우 간단
    - JVM이 직접 관리하여 안정적
    - 추가 라이브러리 불필요
    - 데드락 감지 기능 내장
- **단점**:
    - 단일 JVM 내에서만 동작
    - 성능 오버헤드 존재
    - 세밀한 제어 어려움
    - 타임아웃 설정 불가
- **구현 예시 코드**:
```java
@Service
public class PointService {
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    
    // 메서드 전체 동기화
    public synchronized UserPoint use(long userId, long amount) {
        UserPoint current = userPointTable.selectById(userId);
        UserPoint usedUserPoint = current.use(amount);
        
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
    }
    
    // 블록 레벨 동기화 (유저별 락 분산)
    private final Object[] locks = new Object[100];
    
    public UserPoint useWithBlockSync(long userId, long amount) {
        Object lock = locks[(int)(userId % locks.length)];
        synchronized(lock) {
            UserPoint current = userPointTable.selectById(userId);
            UserPoint usedUserPoint = current.use(amount);
            
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
        }
    }
}
```

### 2. ReentrantLock (애플리케이션 레벨 락)
- **원리**: Java의 java.util.concurrent 패키지에서 제공하는 명시적 락으로, synchronized보다 더 유연한 락 제어 가능
- **장점**:
    - 공정성(fairness) 설정 가능
    - 타임아웃 설정 가능
    - 락 획득 시도 후 포기 가능
    - 조건 변수(Condition) 지원
    - 락 상태 조회 가능
- **단점**:
    - synchronized보다 복잡한 코드
    - 명시적 unlock 필요 (finally 블록 필수)
    - 단일 JVM 내에서만 동작
    - 잘못 사용 시 데드락 위험
- **구현 예시 코드**:
```java
@Service
public class PointService {
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    
    public UserPoint use(long userId, long amount) {
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true)); // 공정성 보장
        
        lock.lock();
        try {
            UserPoint current = userPointTable.selectById(userId);
            UserPoint usedUserPoint = current.use(amount);
            
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
        } finally {
            lock.unlock();
        }
    }
    
    // 타임아웃 설정 버전
    public UserPoint useWithTimeout(long userId, long amount) throws InterruptedException {
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        
        if (lock.tryLock(5, TimeUnit.SECONDS)) {
            try {
                UserPoint current = userPointTable.selectById(userId);
                UserPoint usedUserPoint = current.use(amount);
                
                pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
                return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
            } finally {
                lock.unlock();
            }
        } else {
            throw new RuntimeException("락 획득 실패 - 타임아웃");
        }
    }
}
```

### 3. 낙관적 락 (Optimistic Lock)
- **원리**: 충돌이 자주 일어나지 않을 것이라 가정하고, 데이터 수정 시점에 버전을 체크하여 충돌을 감지
- **장점**: 
    - 성능 좋음 (락 대기 없음)
    - 동시성 높음
    - 데드락 없음
- **단점**: 
    - 충돌 시 재시도 필요
    - 충돌이 잦으면 비효율적
    - 구현 복잡도 증가
- **구현 예시 코드**:
```java
// UserPoint에 version 필드 추가
public record UserPoint(
    long id,
    long point,
    long updateMillis,
    long version  // 버전 필드 추가
) {
    public UserPoint use(long amount) {
        validateUsePoint(amount);
        validateRemainingPoint(amount);
        long newBalance = this.point - amount;
        return new UserPoint(this.id, newBalance, System.currentTimeMillis(), this.version + 1);
    }
}

// UserPointTable에 버전 체크 로직 추가
@Component
public class UserPointTable {
    private final Map<Long, UserPoint> table = new ConcurrentHashMap<>();
    
    public UserPoint insertOrUpdateWithVersion(long id, long amount, long expectedVersion) {
        UserPoint existing = table.get(id);
        
        if (existing != null && existing.version() != expectedVersion) {
            throw new OptimisticLockException("버전 충돌 발생");
        }
        
        UserPoint userPoint = new UserPoint(id, amount, System.currentTimeMillis(), expectedVersion + 1);
        table.put(id, userPoint);
        return userPoint;
    }
}

// PointService
@Service
public class PointService {
    
    @Retryable(value = OptimisticLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public UserPoint use(long userId, long amount) {
        UserPoint current = userPointTable.selectById(userId);
        UserPoint usedUserPoint = current.use(amount);
        
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        
        return userPointTable.insertOrUpdateWithVersion(
            usedUserPoint.id(), 
            usedUserPoint.point(), 
            current.version()
        );
    }
}
```

### 4. 비관적 락 (Pessimistic Lock)
- **원리**: 충돌이 자주 일어날 것이라 가정하고 미리 잠금을 획득한 후 작업 수행
- **장점**:
    - 데이터 정합성 확실히 보장
    - 충돌 시 안전하게 대기
    - 구현이 단순함
- **단점**: 
    - 성능 저하 가능 (락 대기 시간)
    - 데드락 위험
    - 동시성 제한
- **구현 예시 코드**:
```java
// UserPointTable에 SELECT FOR UPDATE 시뮬레이션
@Component
public class UserPointTable {
    private final Map<Long, UserPoint> table = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> rowLocks = new ConcurrentHashMap<>();
    
    // SELECT FOR UPDATE 시뮬레이션
    public UserPoint selectByIdForUpdate(Long id) {
        ReentrantLock lock = rowLocks.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock(); // 트랜잭션 종료시 unlock 필요
        
        throttle(200);
        return table.getOrDefault(id, UserPoint.empty(id));
    }
    
    public void releaseLock(Long id) {
        ReentrantLock lock = rowLocks.get(id);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

// PointService
@Service
public class PointService {
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    
    public UserPoint use(long userId, long amount) {
        try {
            // 비관적 락으로 조회
            UserPoint current = userPointTable.selectByIdForUpdate(userId);
            UserPoint usedUserPoint = current.use(amount);
            
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
        } finally {
            userPointTable.releaseLock(userId);
        }
    }
}
```

### 5. 분산 락 (Distributed Lock)
- **원리**: 여러 서버 인스턴스 간의 동시성을 제어하기 위해 Redis 등 외부 저장소를 이용하여 락을 관리
- **장점**:
    - 다중 서버 환경에서 동시성 제어 가능
    - 확장성 좋음
    - 서버 장애 시에도 락 해제 가능 (TTL)
- **단점**:
    - 네트워크 지연으로 인한 성능 오버헤드
    - 외부 의존성 증가 (Redis 등)
    - 구현 및 관리 복잡도 높음
- **구현 예시 코드**:
```java
// Redis 대신 메모리 기반 분산락 시뮬레이션
@Component
public class DistributedLockManager {
    private final Map<String, Long> locks = new ConcurrentHashMap<>();
    
    public boolean tryLock(String key, Duration timeout) {
        long expiryTime = System.currentTimeMillis() + timeout.toMillis();
        Long existing = locks.putIfAbsent(key, expiryTime);
        
        if (existing == null) {
            return true;
        }
        
        // 만료된 락 체크
        if (existing < System.currentTimeMillis()) {
            locks.remove(key, existing);
            return tryLock(key, timeout);
        }
        
        return false;
    }
    
    public void unlock(String key) {
        locks.remove(key);
    }
}

// PointService
@Service
public class PointService {
    private final DistributedLockManager lockManager;
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    
    public UserPoint use(long userId, long amount) {
        String lockKey = "point:user:" + userId;
        
        if (!lockManager.tryLock(lockKey, Duration.ofSeconds(5))) {
            throw new RuntimeException("다른 요청이 처리중입니다");
        }
        
        try {
            UserPoint current = userPointTable.selectById(userId);
            UserPoint usedUserPoint = current.use(amount);
            
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

### 6. SELECT FOR UPDATE (Database Lock)
- **원리**: 데이터베이스 레벨에서 특정 행에 대한 배타적 락을 걸어 다른 트랜잭션의 접근을 차단
- **장점**:
    - DB가 직접 보장하는 강력한 일관성
    - 트랜잭션과 자동 연동
    - 간단한 구현
- **단점**:
    - 락 대기로 인한 성능 저하
    - 데드락 위험
    - DB 커넥션 점유 시간 증가
- **구현 예시 코드**:
```java
// 실제 JPA Repository 사용 시
@Repository
public interface UserPointRepository extends JpaRepository<UserPoint, Long> {
    
    @Query(value = "SELECT * FROM user_point WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Optional<UserPoint> findByIdForUpdate(@Param("userId") Long userId);
}

// 현재 프로젝트 구조에서 시뮬레이션
@Component
public class UserPointTable {
    private final Map<Long, UserPoint> table = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> dbLocks = new ConcurrentHashMap<>();
    
    // SELECT FOR UPDATE 동작 시뮬레이션
    @Transactional
    public UserPoint selectByIdWithLock(Long id) {
        ReentrantLock lock = dbLocks.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        
        throttle(200);
        return table.getOrDefault(id, UserPoint.empty(id));
    }
    
    @Transactional
    public UserPoint insertOrUpdateWithLock(long id, long amount) {
        throttle(300);
        UserPoint userPoint = new UserPoint(id, amount, System.currentTimeMillis());
        table.put(id, userPoint);
        
        // 트랜잭션 종료 시 자동으로 락 해제
        ReentrantLock lock = dbLocks.get(id);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
        
        return userPoint;
    }
}

// PointService
@Service
@Transactional
public class PointService {
    
    public UserPoint use(long userId, long amount) {
        // SELECT FOR UPDATE로 락 획득
        UserPoint current = userPointTable.selectByIdWithLock(userId);
        UserPoint usedUserPoint = current.use(amount);
        
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        
        // 트랜잭션 종료 시 자동으로 락 해제
        return userPointTable.insertOrUpdateWithLock(usedUserPoint.id(), usedUserPoint.point());
    }
}
```

### 7. 큐 기반 순차 처리 (Queue-based Sequential Processing)
- **원리**: 동시성 문제를 피하기 위해 요청을 큐에 저장하고 순차적으로 처리
- **장점**:
    - 동시성 문제 원천 차단
    - 높은 처리량과 확장성
    - 장애 복구 용이 (재처리 가능)
    - 비동기 처리로 응답 속도 향상
- **단점**:
    - 실시간 처리 어려움
    - 시스템 복잡도 증가
    - 메시지 큐 인프라 필요
    - 결과적 일관성(Eventually Consistent)
- **구현 예시 코드**:
```java
// 포인트 사용 이벤트 클래스
public class PointUseEvent {
    private final long userId;
    private final long amount;
    private final CompletableFuture<UserPoint> result;
    
    public PointUseEvent(long userId, long amount) {
        this.userId = userId;
        this.amount = amount;
        this.result = new CompletableFuture<>();
    }
    
    // getters...
}

// 이벤트 프로세서 (큐 처리)
@Component
public class PointEventProcessor {
    private final BlockingQueue<PointUseEvent> queue = new LinkedBlockingQueue<>();
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;
    
    @PostConstruct
    public void startProcessor() {
        Thread processor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PointUseEvent event = queue.take();
                    processEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        processor.setDaemon(true);
        processor.start();
    }
    
    private void processEvent(PointUseEvent event) {
        try {
            UserPoint current = userPointTable.selectById(event.getUserId());
            UserPoint usedUserPoint = current.use(event.getAmount());
            
            pointHistoryTable.insert(event.getUserId(), event.getAmount(), 
                                    TransactionType.USE, System.currentTimeMillis());
            UserPoint result = userPointTable.insertOrUpdate(usedUserPoint.id(), usedUserPoint.point());
            
            event.getResult().complete(result);
        } catch (Exception e) {
            event.getResult().completeExceptionally(e);
        }
    }
    
    public CompletableFuture<UserPoint> submitUseRequest(long userId, long amount) {
        PointUseEvent event = new PointUseEvent(userId, amount);
        queue.offer(event);
        return event.getResult();
    }
}

// PointService
@Service
public class PointService {
    private final PointEventProcessor eventProcessor;
    
    public UserPoint use(long userId, long amount) throws Exception {
        // 비동기 요청을 동기적으로 대기 (타임아웃 5초)
        return eventProcessor.submitUseRequest(userId, amount)
                            .get(5, TimeUnit.SECONDS);
    }
}

// PointController
@RestController
@RequestMapping("/point")
public class PointController {
    private final PointService pointService;
    
    @PatchMapping("{id}/use")
    public UserPoint use(@PathVariable long id, @RequestBody long amount) {
        return pointService.use(id, amount);
    }
}
```

### 방법별 적용 가이드라인
- **synchronized**: 단일 JVM, 간단한 구현이 필요한 경우
- **ReentrantLock**: 단일 JVM, 타임아웃이나 공정성 제어가 필요한 경우
- **낙관적 락**: 충돌이 적은 환경, 높은 동시성이 필요한 경우
- **비관적 락**: 충돌이 빈번하고 데이터 정합성이 중요한 경우
- **분산 락**: 여러 서버 인스턴스가 동일한 자원에 접근하는 경우
- **SELECT FOR UPDATE**: 단일 DB 환경에서 강력한 일관성이 필요한 경우
- **큐 기반 처리**: 대용량 처리가 필요하고 비동기 처리가 가능한 경우

### 동시성 제어 방법 비교표 (현재 프로젝트 기준)

| 방법 | 범위 | 성능 | 구현 복잡도 | 장점 | 단점 | 적합한 상황 |
|------|------|------|------------|------|------|------------|
| synchronized | JVM | 중간 | 낮음 | 구현 간단, JVM 관리 | 단일 JVM만, 세밀한 제어 어려움 | 빠른 프로토타입 |
| ReentrantLock | JVM | 높음 | 중간 | 유연한 락 제어, 타임아웃 | 명시적 unlock 필요 | 유저별 락 분리 |
| 낙관적 락 | App/DB | 높음 | 높음 | 높은 동시성, 데드락 없음 | 재시도 로직 필요 | 읽기 많은 서비스 |
| 비관적 락 | DB | 낮음 | 중간 | 강력한 일관성 | 성능 저하, 데드락 위험 | 충돌 빈번한 경우 |
| 분산 락 | 분산 | 중간 | 높음 | 다중 서버 지원 | 외부 의존성 | 분산 환경 |
| SELECT FOR UPDATE | DB | 낮음 | 중간 | DB 레벨 보장 | DB 커넥션 점유 | 트랜잭션 필요 |
| 큐 기반 | App | 높음 | 높음 | 동시성 문제 원천 차단 | 실시간성 떨어짐 | 대용량 배치 |

### 포인트 시스템의 경우, 어떤 방법을?
- 포인트 시스템의 특성
1. 잔액이 음수가 되면 안 됨 -> 강력한 일관성 필요
2. 사용자별로 독립적 -> 데드락 위험 낮음
3. 실시간 처리 필요 -> 큐 방식 부적합

### 현재 프로젝트 추천 방안
1. **단기 해결책**: ReentrantLock (유저별 락 분리로 성능 보장)
2. **중기 개선안**: 비관적 락 또는 SELECT FOR UPDATE (DB 레벨 일관성)
3. **장기 최적화**: 낙관적 락 (성능 최적화)
