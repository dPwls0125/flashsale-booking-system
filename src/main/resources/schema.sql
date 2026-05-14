-- ============================================================
-- Flash-Sale Booking System DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS member (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    point_balance   INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    price           INT             NOT NULL,
    check_in_time   TIME            NOT NULL,
    check_out_time  TIME            NOT NULL,
    description     VARCHAR(1000),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_stock (
    id                  BIGINT      AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT      NOT NULL,
    total_quantity      INT         NOT NULL,
    remaining_quantity  INT         NOT NULL,
    CONSTRAINT uk_product_stock_product_id UNIQUE (product_id),
    CONSTRAINT fk_product_stock_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_remaining_quantity CHECK (remaining_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS booking (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT          NOT NULL,
    product_id      BIGINT          NOT NULL,
    idempotency_key VARCHAR(64)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    total_amount    INT             NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_booking_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_booking_member_product UNIQUE (member_id, product_id),
    CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_booking_product FOREIGN KEY (product_id) REFERENCES product (id),
    INDEX idx_booking_member_id (member_id),
    INDEX idx_booking_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    booking_id          BIGINT          NOT NULL,
    payment_method      VARCHAR(20)     NOT NULL,
    amount              INT             NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    pg_transaction_id   VARCHAR(100),
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    INDEX idx_payment_booking_id (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;