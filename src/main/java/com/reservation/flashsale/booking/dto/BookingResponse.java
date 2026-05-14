package com.reservation.flashsale.booking.dto;

public record BookingResponse(
        Long bookingId,
        Long productId,
        String orderName,
        int totalAmount,
        String status
) {
}
