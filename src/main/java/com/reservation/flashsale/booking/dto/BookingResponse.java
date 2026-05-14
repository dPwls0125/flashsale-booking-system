package com.reservation.flashsale.booking.dto;

import lombok.Builder;
import java.time.LocalTime;

@Builder
public record BookingResponse(
        Long bookingId,
        Long productId,
        String orderName,
        int totalAmount,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        Integer memberPoints,
        Integer remainingStock,
        String status
) {
    public static BookingResponse success(Long bookingId, Long productId, String orderName, int totalAmount, 
                                          LocalTime checkInTime, LocalTime checkOutTime, Integer memberPoints, Integer remainingStock) {
        return BookingResponse.builder()
                .bookingId(bookingId)
                .productId(productId)
                .orderName(orderName)
                .totalAmount(totalAmount)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .memberPoints(memberPoints)
                .remainingStock(remainingStock)
                .status("SUCCESS")
                .build();
    }

    public static BookingResponse success(Long bookingId, Long productId, String orderName, int totalAmount) {
        return success(bookingId, productId, orderName, totalAmount, null, null, null, null);
    }
}
