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

## 2. 시퀀스 다이어그램: Booking API (선착순 처리)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Controller as BookingController
    participant Facade as BookingFacade
    participant Local as LocalTrafficLimiter
    participant Redis as RedisStock/Purchase
    participant PG as PaymentGateway(Stub)
    participant DB as MySQL(Booking/Stock/Payment)

    User->>Controller: 예약 요청 (memberId, productId)
    Controller->>Facade: 예약 수행 요청
    
    rect rgb(240, 240, 240)
    Note over Facade, Redis: 1~2단계: 인메모리/캐시 기반 고속 필터링
    Facade->>Local: 1차 통관 (Local Counter)
    Facade->>Redis: 2차 통관 (DECR Stock & Purchase Check)
    end

    Facade->>PG: 결제 승인 요청 (외부 연동)
    PG-->>Facade: 승인 완료 (paymentKey)

    rect rgb(255, 245, 245)
    Note over Facade, DB: 3단계: DB 정합성 확정 및 실패 시 복구
    Facade->>DB: 최종 확정 (Transaction)
    DB->>DB: DB 재고 차감 (Atomic Update)
    DB->>DB: 예약/결제 내역 저장
    
    alt DB 실패 (예: 제약조건 위반)
        DB-->>Facade: Exception
        Facade->>PG: [보상] 결제 취소 요청
        Facade->>Redis: [보상] 재고 복구 (INCR)
        Facade->>Local: [보상] 로컬 카운터 복구
        Facade-->>User: 500/409 Error
    else 성공
        DB-->>Facade: Commit
        Facade-->>User: 200 OK (예약 성공)
    end
    end
```

---

## 3. 데이터베이스 설계 (ERD)

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
        string idempotency_key UK
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
(중략 - 이전과 동일)
