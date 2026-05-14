package com.reservation.flashsale.product.dto;

import com.reservation.flashsale.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class CheckoutResponse {

    private Long productId;
    private String productName;
    private int price;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private String description;
    private int remainingStock;
    private boolean soldOut;
    private int memberPointBalance;

    public static CheckoutResponse of(Product product, int remainingStock, int memberPointBalance) {
        return CheckoutResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .checkInTime(product.getCheckInTime())
                .checkOutTime(product.getCheckOutTime())
                .description(product.getDescription())
                .remainingStock(remainingStock)
                .soldOut(remainingStock <= 0)
                .memberPointBalance(memberPointBalance)
                .build();
    }
}
