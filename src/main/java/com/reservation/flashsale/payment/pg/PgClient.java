package com.reservation.flashsale.payment.pg;

/**
 * PG사 클라이언트 인터페이스.
 * 실제 PG 연동 시 이 인터페이스의 구현체를 교체한다.
 */
public interface PgClient {

    /**
     * 결제 승인 요청.
     */
    PgPaymentResponse confirm(PgPaymentRequest request);

    /**
     * 결제 취소 요청.
     */
    PgCancelResponse cancel(String paymentKey, String cancelReason, int cancelAmount);
}
