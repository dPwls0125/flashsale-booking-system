package com.reservation.flashsale.payment.strategy;

/**
 * 결제 실행 결과.
 *
 * @param success         성공 여부
 * @param pgTransactionId PG사 거래 키 (Y_POINT의 경우 null)
 * @param errorCode       실패 시 PG 에러 코드
 * @param errorMessage    실패 시 PG 에러 메시지
 */
public record PaymentResult(
        boolean success,
        String pgTransactionId,
        String errorCode,
        String errorMessage
) {

    public static PaymentResult success(String pgTransactionId) {
        return new PaymentResult(true, pgTransactionId, null, null);
    }

    public static PaymentResult fail(String errorCode, String errorMessage) {
        return new PaymentResult(false, null, errorCode, errorMessage);
    }
}
