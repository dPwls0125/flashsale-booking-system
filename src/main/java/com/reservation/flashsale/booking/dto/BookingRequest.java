package com.reservation.flashsale.booking.dto;

import com.reservation.flashsale.payment.entity.PaymentMethod;

import java.util.List;

public record BookingRequest(
        List<PaymentMethod> paymentMethods
) {
}
