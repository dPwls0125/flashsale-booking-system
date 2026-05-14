package com.reservation.flashsale.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_idempotency_key", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_booking_member_product", columnNames = {"member_id", "product_id"})
        },
        indexes = {
                @Index(name = "idx_booking_member_id", columnList = "member_id"),
                @Index(name = "idx_booking_product_id", columnList = "product_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Booking(Long memberId, Long productId, String idempotencyKey, int totalAmount) {
        this.memberId = memberId;
        this.productId = productId;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING;
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = BookingStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }
}
