package com.reservation.flashsale.payment.strategy;

/**
 * 결제 실행에 필요한 컨텍스트 정보.
 */
public record PaymentContext(
        String orderId,
        String orderName,
        int amount,
        Long memberId
) {
}
