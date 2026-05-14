# 00:00 선착순 타임세일 예약 시스템

본 프로젝트는 초고부하가 발생하는 선착순 타임세일 환경에서 **데이터 정합성**과 **시스템 가용성**을 보장하기 위한 예약 시스템입니다.

## 1. API 목록 및 전략 분리
시스템은 조회의 부하와 예약의 부하를 분리하여 서로 다른 전략을 적용합니다.

### [GET] Checkout API (주문서 진입)
- **URL**: `/api/booking/checkout/{productId}`
- **목적**: 상품 정보(가격, 재고 현황)와 사용자의 포인트 잔액을 조회합니다.
- **전략**: **고가용성(High Availability) 조회**. 로컬 캐시(Caffeine)를 활용해 DB 부하를 최소화하며, 선착순 로직에 의한 차단 없이 모든 사용자에게 정보를 제공합니다.

### [POST] Booking API (결제 및 예약 실행)
- **URL**: `/api/booking/{productId}`
- **목적**: 실제 재고를 점유하고 결제를 진행하여 예약을 확정합니다.
- **전략**: **엄격한 정합성 및 부하 차단**. 3단계 필터링(Local -> Redis -> DB)을 통해 초과 판매를 방지하고 시스템 붕괴를 막습니다.

---

## 2. 시퀀스 다이어그램

### Checkout API (주문서 진입)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as CheckoutController
    participant Cache as Caffeine(Product)
    participant Redis as Redis(Stock)
    participant DB as MySQL(Product/Member)

    User->>Controller: GET /api/checkout/{productId} (X-Member-Id 헤더)

    Controller->>Cache: 상품 정보 조회 (productId)
    alt 캐시 히트 (30s TTL)
        Cache-->>Controller: Product (캐시)
    else 캐시 미스
        Cache->>DB: SELECT product
        DB-->>Cache: Product
        Cache-->>Controller: Product (DB)
    end

    Controller->>Redis: 재고 수량 조회 (GET stock:product:{id})
    alt Redis 히트
        Redis-->>Controller: remainingStock
    else Redis 미스 또는 장애
        Redis->>DB: SELECT product_stock
        DB-->>Redis: remainingStock
        Redis-->>Controller: remainingStock (DB fallback)
    end

    Controller->>DB: 회원 포인트 조회 (SELECT member)
    DB-->>Controller: pointBalance

    Controller-->>User: 200 OK (상품정보 + 재고 + 포인트)
```

---

### Booking API (선착순 처리)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as BookingController
    participant Facade as BookingFacade
    participant Local as LocalTrafficLimiter
    participant Redis as Redis(Idempotency/Purchase/Stock)
    participant PG as PaymentGateway(Stub)
    participant DB as MySQL(Booking/Stock/Payment)

    User->>Controller: 예약 요청 (memberId, productId, Idempotency-Key)
    Controller->>Facade: 예약 수행 요청
    
    rect rgb(240, 240, 240)
    Note over Facade, Redis: 1~4단계: 인메모리/캐시 기반 고속 필터링
    Facade->>Local: 1단계: 로컬 카운터 차감 (Fail-Fast)
    Facade->>Redis: 2단계: 멱등성 체크 (SETNX idempotency:{key})
    Facade->>Redis: 3단계: 1인1매 체크 (SADD purchased:product:{id})
    Facade->>Redis: 4단계: 재고 차감 (DECR stock:product:{id})
    end

    Facade->>PG: 결제 승인 요청 (외부 연동)
    PG-->>Facade: 승인 완료 (paymentKey)

    rect rgb(255, 245, 245)
    Note over Facade, DB: 5단계: DB 정합성 확정 및 실패 시 복구
    Facade->>DB: 최종 확정 (Transaction)
    DB->>DB: DB 재고 차감 (WHERE remaining > 0)
    DB->>DB: 예약/결제 내역 저장
    
    alt DB 실패 (예: 제약조건 위반)
        DB-->>Facade: Exception
        Facade->>PG: [보상] 결제 취소 요청
        Facade->>Redis: [보상] 재고 복구 (INCR)
        Facade->>Redis: [보상] 구매 이력 삭제 (SREM)
        Facade->>Redis: [보상] 멱등성 키 삭제 (재시도 허용)
        Facade->>Local: [보상] 로컬 카운터 복구
        Facade-->>User: 500/409 Error
    else 성공
        DB-->>Facade: Commit
        Facade->>Redis: 멱등성 키 COMPLETED 상태로 변경
        Facade-->>User: 200 OK (예약 성공)
    end
    end
```

---

## 3. 데이터베이스 설계 (ERD)

> **BOOKING 제약 조건**: `idempotency_key` 단일 UK 외에, `(member_id, product_id)` 복합 UK가 DB 레벨 1인1매 최종 안전망으로 존재합니다.

```mermaid
erDiagram
    MEMBER ||--o{ BOOKING : "makes"
    PRODUCT ||--|| PRODUCT_STOCK : "has"
    PRODUCT ||--o{ BOOKING : "is booked"
    BOOKING ||--o{ PAYMENT : "has"

    MEMBER {
        long id PK
        string name
        long point_balance
    }

    PRODUCT {
        long id PK
        string name
        int price
    }

    PRODUCT_STOCK {
        long id PK
        long product_id FK
        int total_quantity
        int remaining_quantity
    }

    BOOKING {
        long id PK
        long member_id FK
        long product_id FK
        string idempotency_key UK "UK"
        string status "PENDING, CONFIRMED, CANCELLED"
        int total_amount
    }

    PAYMENT {
        long id PK
        long booking_id FK
        string payment_method
        int amount
        string pg_transaction_id
    }
```

---

## 4. 실행 방법

### 사전 준비
- Docker, Docker Compose
- Java 21

### 1단계: 인프라 기동 (MySQL + Redis Sentinel)

```bash
docker-compose up -d mysql redis-master redis-replica-1 redis-replica-2 redis-sentinel-1 redis-sentinel-2 redis-sentinel-3
```

| 서비스 | 로컬 포트 | 설명 |
|---|---|---|
| MySQL | 13306 | DB (ID: root / PW: root / DB: flashsale) |
| Redis Master | 6379 | 재고·멱등성·구매이력 저장 |
| Redis Replica | 6380, 6381 | 읽기 복제본 |
| Redis Sentinel | 26379~26381 | Master 장애 감지 및 자동 Failover |

### 2단계: 애플리케이션 실행

```bash
./gradlew bootRun
```

- 기동 시 `StockInitializer`가 DB 재고를 Redis와 로컬 카운터에 자동으로 로드합니다.
- 초기 데이터(회원 3명, 상품 1개, 재고 10개)는 `data.sql`로 자동 삽입됩니다.

> **참고**: Redis Sentinel 로컬 접속 이슈가 있는 경우 `application.properties`의 Sentinel 설정 대신 standalone 설정(`spring.data.redis.host=localhost`, `port=6379`)을 사용하세요.

### 3단계: API 호출 예시

**주문서 조회**
```bash
curl http://localhost:8080/api/booking/checkout/1 \
  -H "X-Member-Id: 1"
```

**예약 실행**
```bash
curl -X POST http://localhost:8080/api/booking/1 \
  -H "Content-Type: application/json" \
  -H "X-Member-Id: 1" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"paymentMethods": ["CREDIT_CARD"]}'
```

---

## 5. 부하 테스트 결과 (k6)

### 테스트 목표
> 1000 TPS 환경에서 재고 10개 한정 상품에 대해 **정확히 10건만 예약이 성공**해야 한다.

### 테스트 환경
- 도구: k6
- 시나리오: `constant-arrival-rate` 1,000 req/s × 3초 (총 약 3,000건)
- 가상 사용자: 최대 500 VU
- 요청 구성: 회원 2,000명 중 무작위 선택, 각각 다른 `Idempotency-Key`

```bash
k6 run load-test.js
```

### 테스트 결과

| 항목 | 결과 |
|---|---|
| 총 요청 수 | 2,977건 (목표 3,000건, dropped 24건) |
| 실제 TPS | **991.8 req/s** |
| 예약 성공 (200) | **10건** ✅ |
| 품절 응답 (409) | 2,967건 |
| 예외 오류 (5xx) | **0건** ✅ |
| 평균 응답 시간 | 11.12ms |
| p(90) 응답 시간 | 1.91ms |
| p(95) 응답 시간 | 95.74ms |
| DB 최종 확정 예약 | **10건 (CONFIRMED)** ✅ |
| DB 잔여 재고 | **0개** ✅ |

### 검증 포인트
- 약 3,000건의 동시 요청 중 **재고 수량과 정확히 일치하는 10건만 성공** — 초과 판매 없음
- 5xx 예외 오류 **0건** — 시스템 안정성 확인
- DB CONFIRMED 예약 수와 성공 응답 수가 **완전히 일치** — 데이터 정합성 확인
