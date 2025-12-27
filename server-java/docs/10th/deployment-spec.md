# 콘서트 예약 시스템 배포 스펙 권장안

## 1. 개요

부하 테스트 결과를 바탕으로 안정적인 서비스 운영을 위한 배포 스펙을 제안합니다.

---

## 2. 테스트 결과 요약

| 부하 수준 | 동시 사용자 | TPS | 에러율 | 평가 |
|----------|-----------|-----|--------|------|
| 기본 | 350 | 4,523 | 0.00% | 안정 |
| 고부하 (개선 전) | 730 | 4,479 | 3.30% | 불안정 |
| 고부하 (개선 후) | 730 | 4,500+ | **0.01%** | **안정** |

**권장 최대 동시 사용자: 730명+** (성능 개선 완료)

---

## 3. 권장 배포 스펙

### 3.1 애플리케이션 서버

#### Minimum (개발/테스트 환경)
```yaml
resources:
  cpu: 1 core
  memory: 1GB
  jvm_heap: 512MB

capacity:
  concurrent_users: ~100
  tps: ~1,000
```

#### Standard (소규모 운영)
```yaml
resources:
  cpu: 2 cores
  memory: 2GB
  jvm_heap: 1GB

capacity:
  concurrent_users: ~300
  tps: ~3,000

config:
  hikari.maximum-pool-size: 10
  server.tomcat.threads.max: 200
```

#### Production (권장)
```yaml
resources:
  cpu: 4 cores
  memory: 4GB
  jvm_heap: 2GB

capacity:
  concurrent_users: ~500
  tps: ~5,000

config:
  hikari.maximum-pool-size: 20
  server.tomcat.threads.max: 400
  server.tomcat.accept-count: 100
```

#### High Performance (대규모 이벤트)
```yaml
resources:
  cpu: 8 cores
  memory: 8GB
  jvm_heap: 4GB
  instances: 2+ (Load Balanced)

capacity:
  concurrent_users: ~2,000
  tps: ~10,000+

config:
  hikari.maximum-pool-size: 30
  server.tomcat.threads.max: 500
```

### 3.2 MySQL 데이터베이스

#### Standard
```yaml
resources:
  cpu: 2 cores
  memory: 4GB
  storage: SSD 100GB

config:
  max_connections: 200
  innodb_buffer_pool_size: 2GB
  innodb_log_file_size: 256MB
```

#### Production
```yaml
resources:
  cpu: 4 cores
  memory: 8GB
  storage: SSD 200GB

config:
  max_connections: 500
  innodb_buffer_pool_size: 5GB
  innodb_log_file_size: 512MB

replication:
  type: Master-Slave
  read_replicas: 1+
```

### 3.3 Redis

#### Standard
```yaml
resources:
  cpu: 1 core
  memory: 1GB

config:
  maxmemory: 512MB
  maxmemory-policy: volatile-lru
  timeout: 0
```

#### Production
```yaml
resources:
  cpu: 2 cores
  memory: 4GB

config:
  maxmemory: 2GB
  maxmemory-policy: volatile-lru
  appendonly: yes
  appendfsync: everysec

cluster:
  type: Sentinel or Cluster
  replicas: 2+
```

---

## 4. 설정 변경 권장

### 4.1 application.yml 수정안

```yaml
# 적용 완료된 설정
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 3 → 20 (적용 완료)
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

server:
  tomcat:
    threads:
      max: 400
      min-spare: 20
    accept-count: 100
    max-connections: 8192
```

### 4.2 JVM 옵션

```bash
# Production 권장
JAVA_OPTS="-Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/heapdump.hprof \
  -Djava.security.egd=file:/dev/./urandom"
```

---

## 5. Docker Compose 권장 설정

```yaml
version: '3.8'

services:
  app:
    image: concert-reservation:latest
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 4G
        reservations:
          cpus: '2'
          memory: 2G
    environment:
      - JAVA_OPTS=-Xms2g -Xmx2g -XX:+UseG1GC
      - SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20
    depends_on:
      - mysql
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  mysql:
    image: mysql:8.0
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 8G
    command: >
      --max_connections=500
      --innodb_buffer_pool_size=5G
      --innodb_log_file_size=512M
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
    command: >
      redis-server
      --maxmemory 1gb
      --maxmemory-policy volatile-lru
      --appendonly yes
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

---

## 6. 스케일링 전략

### 6.1 수직 확장 (Scale-Up)

| 단계 | 트리거 | 액션 |
|------|--------|------|
| 1 | CPU > 70% | CPU 코어 추가 |
| 2 | Memory > 80% | 메모리 증설 |
| 3 | DB Connection > 80% | Pool Size 증가 |

### 6.2 수평 확장 (Scale-Out)

| 단계 | 트리거 | 액션 |
|------|--------|------|
| 1 | 동시 사용자 > 500 | 앱 인스턴스 추가 |
| 2 | DB 부하 > 70% | Read Replica 추가 |
| 3 | Redis 부하 > 70% | Redis Cluster 구성 |

### 6.3 이벤트 대응 전략

**대규모 티켓팅 이벤트 대비:**

```
사전 준비 (D-1):
1. 앱 인스턴스 2배 증설
2. DB Read Replica 활성화
3. Redis 메모리 확보
4. CDN 캐시 워밍

이벤트 중:
1. 모니터링 강화 (1분 간격)
2. Auto-scaling 활성화
3. Rate Limiting 적용

사후:
1. 리소스 정상화
2. 성능 데이터 분석
```

---

## 7. 비용 추정 (AWS 기준)

### 7.1 Standard 환경 (월간)

| 서비스 | 스펙 | 비용 (USD) |
|--------|------|-----------|
| EC2 (App) | t3.large (2vCPU, 8GB) | ~$60 |
| RDS MySQL | db.t3.medium | ~$50 |
| ElastiCache Redis | cache.t3.small | ~$25 |
| ELB | Application LB | ~$20 |
| **합계** | | **~$155/월** |

### 7.2 Production 환경 (월간)

| 서비스 | 스펙 | 비용 (USD) |
|--------|------|-----------|
| EC2 (App) x2 | t3.xlarge (4vCPU, 16GB) | ~$240 |
| RDS MySQL | db.r5.large + Read Replica | ~$300 |
| ElastiCache Redis | cache.r5.large | ~$150 |
| ELB | Application LB | ~$30 |
| **합계** | | **~$720/월** |

---

## 8. 모니터링 지표

### 8.1 핵심 지표 (SLI)

| 지표 | 임계값 (Warning) | 임계값 (Critical) |
|------|-----------------|------------------|
| 응답 시간 P95 | > 500ms | > 1000ms |
| 에러율 | > 0.5% | > 1% |
| CPU 사용률 | > 70% | > 90% |
| Memory 사용률 | > 70% | > 85% |
| DB Connection | > 70% | > 90% |
| Redis Memory | > 70% | > 85% |

### 8.2 대시보드 구성

```
[System Overview]
├── Request Rate (TPS)
├── Error Rate (%)
├── Response Time (P50, P95, P99)
└── Active Users

[Application]
├── JVM Heap Usage
├── GC Pause Time
├── Thread Count
└── HikariCP Active Connections

[Database]
├── Queries per Second
├── Slow Queries
├── Connection Count
└── Replication Lag

[Redis]
├── Commands per Second
├── Memory Usage
├── Connected Clients
└── Cache Hit Rate
```

---

## 9. 결론

### 9.1 적용 완료 사항

1. **HikariCP max-pool-size 증가**: 3 → 20 (적용 완료)
2. **Redisson Connection Pool**: 50 → 100 (적용 완료)
3. **대기열 Lua 스크립트 원자화**: Race Condition 해결 (적용 완료)
4. **TokenExceptionHandler 추가**: 중복 요청 HTTP 409 반환 (적용 완료)

### 9.2 운영 권장 스펙

| 환경 | 앱 서버 | MySQL | Redis |
|------|---------|-------|-------|
| 개발 | 1 core / 1GB | 1 core / 2GB | 0.5 core / 512MB |
| 스테이징 | 2 cores / 2GB | 2 cores / 4GB | 1 core / 1GB |
| **운영** | **4 cores / 4GB** | **4 cores / 8GB** | **2 cores / 2GB** |

### 9.3 검증된 성능

성능 개선 후 실측 결과:
- **동시 사용자: 730명**
- **TPS: 4,500+**
- **응답 시간 P95: ~50ms**
- **에러율: 0.01%**
