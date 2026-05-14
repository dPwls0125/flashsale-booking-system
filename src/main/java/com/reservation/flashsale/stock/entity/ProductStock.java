package com.reservation.flashsale.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    public ProductStock(Long productId, int totalQuantity) {
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
    }

    public boolean isSoldOut() {
        return this.remainingQuantity <= 0;
    }
}
