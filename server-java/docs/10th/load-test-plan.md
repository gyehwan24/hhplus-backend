# 콘서트 예약 시스템 부하 테스트 계획서

## 1. 개요

### 1.1 문서 목적
본 문서는 콘서트 예약 시스템의 부하 테스트 계획을 정의합니다. 시스템이 실제 운영 환경에서 예상되는 트래픽을 안정적으로 처리할 수 있는지 검증하고, 적절한 배포 스펙을 도출하는 것을 목표로 합니다.

### 1.2 시스템 개요

**콘서트 예약 시스템 아키텍처:**

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│ Spring Boot │────▶│    MySQL    │
│  (JMeter)   │     │ Application │     │   (3307)    │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │    Redis    │
                    │   (6379)    │
                    └─────────────┘
```

**기술 스택:**
- Java 17 + Spring Boot 3.4.1
- MySQL 8.0 (HikariCP, max-pool-size: 3)
- Redis 7 (Redisson, 대기열/캐시)
- JPA/Hibernate

### 1.3 테스트 배경 및 필요성

콘서트 티켓팅 시스템의 특성상 다음과 같은 상황이 발생합니다:

1. **트래픽 급증**: 인기 콘서트 티켓 오픈 시 수천~수만 명이 동시 접속
2. **읽기/쓰기 비율 불균형**: 좌석 조회(읽기)가 예약(쓰기)보다 압도적으로 많음
3. **동시성 이슈**: 동일 좌석에 대한 동시 예약 요청 처리 필요
4. **대기열 부하**: Redis 기반 대기열 시스템의 안정성 검증 필요

---

## 2. 테스트 대상 및 목적

### 2.1 테스트 대상 API

| API | HTTP Method | Endpoint | 설명 | 캐시 TTL |
|-----|-------------|----------|------|----------|
| 대기열 토큰 발급 | POST | `/api/queue/token` | 대기열 진입 | - |
| 대기열 상태 조회 | GET | `/api/queue/status?userId={id}` | 대기/활성 상태 확인 | - |
| 대기열 순번 조회 | GET | `/api/queue/position?userId={id}` | 대기 순번 확인 | - |
| 콘서트 목록 조회 | GET | `/api/concerts` | 전체 콘서트 목록 | 5분 |
| 콘서트 상세 조회 | GET | `/api/concerts/{id}` | 콘서트 상세 정보 | 10분 |
| 스케줄 조회 | GET | `/api/concerts/{id}/schedules` | 예약 가능 일정 | 3분 |
| 좌석 조회 | GET | `/api/schedules/{id}/seats` | 예약 가능 좌석 | 10초 |
| 콘서트 랭킹 | GET | `/api/concerts/ranking` | 인기 콘서트 순위 | - |

### 2.2 테스트 목적

| 목적 | 설명 | 측정 지표 |
|------|------|----------|
| **성능 기준선 측정** | 현재 시스템의 최대 처리량 파악 | TPS, 응답 시간 |
| **병목 구간 식별** | 부하 증가 시 성능 저하 지점 발견 | 에러율, P99 응답 시간 |
| **캐시 효과 검증** | Redis 캐시의 성능 향상 효과 측정 | 캐시 Hit Rate, DB 부하 |
| **동시성 검증** | 대기열 시스템의 동시 처리 능력 | 토큰 발급 TPS |
| **배포 스펙 도출** | 목표 트래픽 처리를 위한 리소스 산정 | CPU/Memory 사용률 |

### 2.3 테스트 우선순위

부하가 집중되고 비즈니스 임팩트가 큰 순서:

| 우선순위 | 기능 | 선정 이유 |
|---------|------|----------|
| 1 | 대기열 토큰 발급 | 티켓팅 오픈 시 가장 먼저 폭주, Redis 병목 예상 |
| 2 | 좌석 조회 | 가장 빈번한 API 호출, 짧은 캐시 TTL(10초) |
| 3 | 대기열 상태 조회 | 폴링으로 인한 지속적 부하 |
| 4 | 콘서트/스케줄 조회 | 읽기 트래픽 대부분 차지, 캐시 효과 검증 |

---

## 3. 테스트 시나리오

### 3.1 시나리오 1: 티켓팅 오픈 시뮬레이션 (메인 시나리오)

**목적:** 실제 티켓팅 오픈 상황을 시뮬레이션하여 전체 시스템 성능 검증

**사용자 흐름:**
```
1. 대기열 토큰 발급 (POST /api/queue/token)
   ↓
2. 대기열 상태 확인 (GET /api/queue/status) - 3초 간격 폴링
   ↓ (ACTIVE 상태가 될 때까지 반복)
3. 콘서트 목록 조회 (GET /api/concerts)
   ↓
4. 콘서트 상세 조회 (GET /api/concerts/{id})
   ↓
5. 스케줄 조회 (GET /api/concerts/{id}/schedules)
   ↓
6. 좌석 조회 (GET /api/schedules/{id}/seats)
```

**부하 프로파일:**
| 단계 | 동시 사용자 | Ramp-up | 지속 시간 | 목적 |
|------|-----------|---------|----------|------|
| Warm-up | 10 | 10초 | 1분 | 시스템 예열, JIT 컴파일 |
| Step 1 | 50 | 30초 | 3분 | 기본 성능 측정 |
| Step 2 | 100 | 30초 | 3분 | 중간 부하 |
| Step 3 | 200 | 60초 | 3분 | 고부하 |
| Step 4 | 500 | 60초 | 3분 | 스트레스 테스트 |
| Peak | 1000 | 120초 | 2분 | 최대 부하 |
| Cool-down | 10 | 30초 | 1분 | 정상화 확인 |

### 3.2 시나리오 2: 읽기 부하 집중 테스트

**목적:** 조회 API의 캐시 효과 및 최대 처리량 측정

**대상 API:**
- 콘서트 목록 조회 (100 threads)
- 콘서트 상세 조회 (50 threads)
- 스케줄 조회 (50 threads)
- 좌석 조회 (200 threads) - 핵심

**측정 항목:**
- 캐시 Hit/Miss 비율
- DB 쿼리 수
- 응답 시간 분포

### 3.3 시나리오 3: 대기열 스트레스 테스트

**목적:** Redis 기반 대기열 시스템의 한계 성능 측정

**테스트 방법:**
1. 1000명 동시 토큰 발급 요청
2. 토큰 발급 후 상태 조회 폴링 (3초 간격)
3. 스케줄러 동작 확인 (10초마다 100명 활성화)

**측정 항목:**
- 토큰 발급 TPS
- Redis 명령 처리량
- 대기열 순번 정확성

---

## 4. 테스트 환경

### 4.1 인프라 구성

**현재 설정 (docker-compose.yml):**
```yaml
MySQL:
  - Image: mysql:8.0
  - Port: 3307
  - 리소스: 제한 없음 (호스트 공유)

Redis:
  - Image: redis:7-alpine
  - Port: 6379
  - AOF Persistence: enabled

Application:
  - JVM: Java 17
  - HikariCP: max-pool-size=3
  - Scheduler: thread-pool-size=2
```

### 4.2 리소스별 테스트 케이스

애플리케이션 리소스를 단계별로 조정하여 적정 스펙 탐색:

| 케이스 | CPU | Memory | JVM Heap | 목적 |
|--------|-----|--------|----------|------|
| Minimum | 1 core | 512MB | 256MB | 최소 스펙 한계 |
| Standard | 2 cores | 1GB | 512MB | 권장 스펙 탐색 |
| High | 4 cores | 2GB | 1GB | 성능 상한선 |

### 4.3 테스트 데이터

| 데이터 | 수량 | 비고 |
|--------|------|------|
| 콘서트 | 10개 | 다양한 상태 |
| 스케줄 | 50개 | 콘서트당 5개 |
| 좌석 | 5,000개 | 스케줄당 100석 |
| 사용자 | 10,000명 | 테스트용 ID |

---

## 5. 성능 목표 (SLO)

### 5.1 응답 시간

| API 유형 | P50 | P95 | P99 | 비고 |
|----------|-----|-----|-----|------|
| 조회 API (캐시) | < 50ms | < 100ms | < 200ms | 캐시 Hit 시 |
| 조회 API (DB) | < 100ms | < 300ms | < 500ms | 캐시 Miss 시 |
| 토큰 발급 | < 100ms | < 300ms | < 500ms | Redis 연산 |
| 상태 조회 | < 50ms | < 100ms | < 200ms | Redis 조회 |

### 5.2 처리량 (TPS)

| API | 목표 TPS | 비고 |
|-----|----------|------|
| 콘서트 목록 조회 | > 500 | 캐시 활용 |
| 좌석 조회 | > 300 | 짧은 TTL |
| 토큰 발급 | > 100 | 동시성 제어 |
| 상태 조회 | > 500 | Redis 단순 조회 |

### 5.3 안정성

| 지표 | 목표값 | 임계값 |
|------|--------|--------|
| 에러율 | < 0.1% | < 1% |
| CPU 사용률 | < 70% | < 90% |
| Memory 사용률 | < 70% | < 85% |
| DB Connection | < 80% | < 95% |

---

## 6. 테스트 도구 및 방법

### 6.1 테스트 도구

- **JMeter 5.6.3**: 부하 생성 및 결과 수집
- **Docker**: 리소스 제한 및 환경 격리
- **Redis CLI**: Redis 상태 모니터링

### 6.2 JMeter 테스트 계획 구성

```
Test Plan: Concert Reservation Load Test (10th)
├── User Defined Variables
│   ├── BASE_URL: localhost
│   ├── PORT: 8080
│   └── DURATION: 300
├── HTTP Request Defaults
├── HTTP Header Manager (Content-Type: application/json)
│
├── Thread Group 1: Token Issue (토큰 발급)
│   ├── POST /api/queue/token
│   └── Response Assertion (200)
│
├── Thread Group 2: Queue Status Polling (상태 조회)
│   ├── GET /api/queue/status
│   └── Constant Timer (3000ms)
│
├── Thread Group 3: Concert List (콘서트 목록)
│   ├── GET /api/concerts
│   └── Response Assertion (200)
│
├── Thread Group 4: Concert Detail (콘서트 상세)
│   ├── GET /api/concerts/${CONCERT_ID}
│   └── Response Assertion (200)
│
├── Thread Group 5: Schedule List (스케줄 조회)
│   ├── GET /api/concerts/${CONCERT_ID}/schedules
│   └── Response Assertion (200)
│
├── Thread Group 6: Seat List (좌석 조회) - CORE
│   ├── GET /api/schedules/${SCHEDULE_ID}/seats
│   └── Response Assertion (200)
│
├── Summary Report
├── Aggregate Report
└── Response Time Graph
```

### 6.3 테스트 실행 명령

```bash
# CLI 모드 실행 (권장)
jmeter -n -t concert-load-test-10th.jmx \
  -l results.jtl \
  -e -o ./report \
  -JDURATION=300 \
  -JTHREADS=100

# HTML 리포트 생성
jmeter -g results.jtl -o ./html-report
```

---

## 7. 측정 지표 및 모니터링

### 7.1 JMeter 수집 지표

| 지표 | 설명 | 수집 방법 |
|------|------|----------|
| Response Time | 요청-응답 시간 | Summary Report |
| Throughput | 초당 처리량 (TPS) | Aggregate Report |
| Error Rate | 에러 비율 | Summary Report |
| Percentiles | P50, P90, P95, P99 | Aggregate Report |

### 7.2 시스템 모니터링

```bash
# Docker 리소스 모니터링
docker stats

# MySQL 연결 수 확인
mysql -h 127.0.0.1 -P 3307 -u application -p -e "SHOW STATUS LIKE 'Threads_connected';"

# Redis 상태 확인
redis-cli INFO stats | grep instantaneous_ops_per_sec
redis-cli INFO clients | grep connected_clients
```

---

## 8. 결과 분석 계획

### 8.1 분석 항목

1. **응답 시간 분포**: 목표 SLO 달성 여부
2. **처리량 추이**: 부하 증가에 따른 TPS 변화
3. **에러 분석**: 에러 유형 및 발생 시점
4. **병목 구간**: 성능 저하 시작 지점
5. **리소스 상관관계**: CPU/Memory와 성능 연관성

### 8.2 결과 문서

테스트 완료 후 다음 문서 작성:
- `load-test-result.md`: 테스트 결과 상세 보고서
- `deployment-spec.md`: 권장 배포 스펙
- HTML 리포트: JMeter 자동 생성

---

## 9. 리스크 및 제약사항

### 9.1 알려진 제약사항

| 제약사항 | 영향 | 대응 방안 |
|----------|------|----------|
| DB Connection Pool 작음 (3) | 동시 요청 병목 | 테스트 시 단계적 확대 |
| 로컬 환경 테스트 | 네트워크 지연 없음 | 실 환경 대비 보수적 해석 |
| 단일 JMeter 인스턴스 | 부하 생성 한계 | 분산 테스트 고려 |

### 9.2 예상 리스크

| 리스크 | 가능성 | 대응 |
|--------|--------|------|
| Redis 메모리 부족 | 중 | maxmemory 설정 확인 |
| HikariCP 고갈 | 높음 | Pool size 조정 테스트 |
| JVM OOM | 중 | Heap 모니터링 |

---

## 10. 일정 및 담당

### 10.1 테스트 진행 순서

| 순서 | 작업 | 산출물 |
|------|------|--------|
| 1 | 테스트 환경 구성 | docker-compose 확인 |
| 2 | JMeter 스크립트 작성 | .jmx 파일 |
| 3 | 시나리오 1 실행 | 결과 데이터 |
| 4 | 시나리오 2 실행 | 결과 데이터 |
| 5 | 시나리오 3 실행 | 결과 데이터 |
| 6 | 리소스별 테스트 | 비교 데이터 |
| 7 | 결과 분석 및 보고서 | load-test-result.md |

---

## 부록

### A. 참고 문서
- 6주차 JMeter 테스트: `docs/6th/jmeter/concert-load-test.jmx`
- 시스템 아키텍처: `CLAUDE.md`

### B. 용어 정의
- **TPS**: Transactions Per Second
- **P95/P99**: 95/99 백분위 응답 시간
- **SLO**: Service Level Objective
- **Ramp-up**: 부하 증가 시간
