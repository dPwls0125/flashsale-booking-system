# 선착순 예약 시스템 — 개발 컨텍스트

## 기술 스택
- Java 17+, Spring Boot 3.x
- Spring Data JPA + MySQL (InnoDB)
- Redis (Spring Data Redis)
- Caffeine (로컬 캐시)
- Docker Compose (MySQL + Redis + App)

---

## ERD

### MEMBER
| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR | NOT NULL |
| point_balance | INT | NOT NULL, DEFAULT 0 |
| created_at | DATETIME | NOT NULL |

### PRODUCT
| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR | NOT NULL |
| price | INT | NOT NULL |
| check_in_time | TIME | NOT NULL |
| check_out_time | TIME | NOT NULL |
| description | VARCHAR | |
| created_at | DATETIME | NOT NULL |

### PRODUCT_STOCK
| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| product_id | BIGINT | FK → PRODUCT.id, UNIQUE |
| total_quantity | INT | NOT NULL |
| remaining_quantity | INT | NOT NULL |

### BOOKING
| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| member_id | BIGINT | FK → MEMBER.id |
| product_id | BIGINT | FK → PRODUCT.id |
| idempotency_key | VARCHAR | UNIQUE |
| status | VARCHAR | NOT NULL (PENDING, CONFIRMED, FAILED, CANCELLED) |
| total_amount | INT | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

- UNIQUE(member_id, product_id) → 1인 1건 제한

### PAYMENT
| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| booking_id | BIGINT | FK → BOOKING.id |
| payment_method | VARCHAR | NOT NULL (CREDIT_CARD, Y_PAY, Y_POINT) |
| amount | INT | NOT NULL |
| status | VARCHAR | NOT NULL (PENDING, SUCCESS, FAILED, REFUNDED) |
| pg_transaction_id | VARCHAR | |
| created_at | DATETIME | NOT NULL |

---

## 패키지 구조

```
com.example.booking
├── product/
│   ├── controller/    ← Checkout API
│   ├── service/
│   ├── repository/
│   └── entity/
├── stock/
│   ├── service/       ← Redis 재고 + DB 재고
│   └── repository/
├── booking/
│   ├── controller/    ← Booking API
│   ├── service/       ← 전체 흐름 조립
│   ├── repository/
│   └── entity/
├── payment/
│   ├── service/       ← 전략 패턴 인터페이스 + 구현체들
│   └── entity/
├── member/
│   ├── service/
│   ├── repository/
│   └── entity/
└── common/
    ├── idempotency/   ← 멱등성 처리
    ├── config/        ← Redis, Caffeine 설정
    └── exception/     ← 글로벌 예외 처리
```

---

## API 흐름

### GET /api/checkout/{productId} — 주문서 진입
```
1. 상품 정보 조회 → 로컬 캐시(Caffeine) → Redis → DB
2. 재고 확인 → Redis GET (차감 아님, 확인만)
3. 사용자 포인트 조회 → DB
4. 응답: 상품 정보 + 재고 여부 + 사용자 포인트
```

### POST /api/booking — 결제 및 예약
```
1. 멱등성 체크     → Redis SETNX idempotency:{key} PROCESSING
                     - 이미 PROCESSING → "처리 중" 리턴
                     - 이미 COMPLETED → "완료된 주문" 리턴
2. 1인 1건 체크    → Redis SADD purchased:product:{id} {memberId}
                     - 리턴 0이면 이미 구매 → 실패
3. 재고 차감       → Redis DECR stock:product:{id}
                     - 리턴값 >= 0이면 성공
                     - < 0이면 품절 → INCR 복구
4. 결제 처리       → 전략 패턴으로 결제 수단별 실행
                     - 복합 결제 시 순서대로 실행
5. DB 저장         → product_stock UPDATE + booking INSERT + payment INSERT
6. 멱등성 키 상태  → 성공: COMPLETED로 변경 / 실패: 키 삭제
```

### 실패 시 보상 트랜잭션 (역순)
```
1. 이미 성공한 결제 수단 취소
2. Redis 재고 복구 (INCR)
3. Redis 1인1건 제거 (SREM)
4. booking 상태 FAILED
5. 멱등성 키 삭제 (재시도 허용)
```

---

## 핵심 설계 결정

### 재고 정합성
- Redis DECR atomic 연산으로 1차 재고 차감
- DB UPDATE로 최종 확정: `UPDATE product_stock SET remaining_quantity = remaining_quantity - 1 WHERE id = ? AND remaining_quantity > 0`
- JPA dirty checking 사용 금지 → @Modifying @Query로 직접 쿼리
- 로컬 카운터(AtomicInteger) 미사용: 1000 TPS에서 Redis 충분, 공정성 훼손 방지

### 공정성 정의
- "시스템 구조로 인한 차별이 없는 것"
- 단일 Redis 재고 차감으로 서버 경유와 무관하게 동일 조건 보장
- 1인 1건 제한: Redis SADD + DB unique(member_id, product_id)

### 멱등성
- Redis SETNX로 요청 진입 시 즉시 체크 (TTL 설정)
- 상태 관리: PROCESSING → COMPLETED (성공) / 삭제 (실패, 재시도 허용)
- booking.idempotency_key unique index → DB 레벨 안전망 + 사후 추적용

### 상품 정보 캐싱
- 이중 캐시: 로컬 캐시(Caffeine, TTL 10~30초) + Redis(TTL 길게)
- 재고는 로컬 캐시에 넣지 않음 (실시간 변경 데이터)
- 사용자 포인트는 DB 직접 조회

### 결제 확장성
- PaymentStrategy 인터페이스: pay(), cancel()
- 구현체: CreditCardPaymentStrategy, YPayPaymentStrategy, YPointPaymentStrategy
- 복합 결제: 해당 Strategy 순서대로 실행
- 신용카드 + Y페이 혼용 불가 → 요청 검증 단계에서 차단
- 부분 실패 시 이미 성공한 결제 취소 (보상 트랜잭션)

### 테이블 분리 (PRODUCT vs PRODUCT_STOCK)
- InnoDB는 row-level lock, column-level lock 미지원
- 재고 차감 시 stock row만 lock → 락 점유 시간 최소화
- 캐싱 전략 분리: 상품 정보(장기 캐싱) vs 재고(실시간)
- product는 숙박업소 자체가 아닌 "특정 날짜/시간대 판매 단위"

### Redis 장애 대응
- Circuit Breaker로 장애 감지 → 즉시 fallback
- 재고 차감: DB UPDATE로 전환
- 멱등성: booking.idempotency_key unique constraint
- 1인 1건: booking (member_id + product_id) unique constraint
- 상품 캐시: DB 직접 조회
- Sentinel/Cluster는 인프라 구성 영역, 과제에서는 애플리케이션 레벨 Fallback에 집중

### 결제 실패 대응
- 실패 시 역순 보상: 결제 취소 → Redis INCR → SREM → booking FAILED → 멱등성 키 삭제

---

## 개발 순서
1. 엔티티 + DDL (전체 테이블)
2. product + stock (Checkout API)
3. payment (전략 패턴)
4. booking (Booking API — 전체 흐름 조립)
5. 장애 대응 (Circuit Breaker, Fallback, 보상 트랜잭션)
6. 테스트 + README + DECISIONS.md
