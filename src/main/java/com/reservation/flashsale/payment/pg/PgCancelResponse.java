package com.reservation.flashsale.payment.pg;

import java.time.LocalDateTime;

/**
 * PG사 결제 취소 응답 DTO.
 */
public record PgCancelResponse(
        String paymentKey,
        String transactionKey,
        String cancelStatus,
        int cancelAmount,
        String cancelReason,
        LocalDateTime canceledAt,
        PgPaymentResponse.PgFailure failure) {

    public boolean isSuccess() {
        return "DONE".equals(cancelStatus);
    }

    public static PgCancelResponse success(String paymentKey, String transactionKey,
            int cancelAmount, String cancelReason) {
        return new PgCancelResponse(
                paymentKey, transactionKey, "DONE", cancelAmount, cancelReason,
                LocalDateTime.now(), null);
    }

    public static PgCancelResponse fail(String paymentKey, String errorCode, String errorMessage) {
        return new PgCancelResponse(
                paymentKey, null, "FAILED", 0, null,
                null, new PgPaymentResponse.PgFailure(errorCode, errorMessage));
    }
}
