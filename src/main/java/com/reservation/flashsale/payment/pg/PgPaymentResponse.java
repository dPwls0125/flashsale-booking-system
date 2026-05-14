package com.reservation.flashsale.payment.pg;

import java.time.LocalDateTime;

/**
 * PG사 결제 승인 응답 DTO.
 */
public record PgPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        int totalAmount,
        String method,
        LocalDateTime approvedAt,
        PgFailure failure) {

    public boolean isSuccess() {
        return "DONE".equals(status);
    }

    /**
     * 결제 승인 실패 시 code와 message를 담는다.
     */
    public record PgFailure(String code, String message) {
    }

    public static PgPaymentResponse success(String paymentKey, String orderId, int amount, String method) {
        return new PgPaymentResponse(
                paymentKey, orderId, "DONE", amount, method,
                LocalDateTime.now(), null);
    }

    public static PgPaymentResponse fail(String orderId, String errorCode, String errorMessage) {
        return new PgPaymentResponse(
                null, orderId, "ABORTED", 0, null,
                null, new PgFailure(errorCode, errorMessage));
    }
}
