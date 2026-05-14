package com.reservation.flashsale.payment.pg;

/**
 * PG사 결제 승인 요청 DTO.
 * 토스페이먼츠 POST /v1/payments/confirm 요청 본문을 참고하여 설계.
 */
public record PgPaymentRequest(
        String orderId,
        String orderName,
        int amount
) {
}
