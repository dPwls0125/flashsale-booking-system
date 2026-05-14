package com.reservation.flashsale.payment.strategy;

/**
 * 결제 수단별 전략 인터페이스.
 * 각 결제 수단(신용카드, Y페이, Y포인트)은 이 인터페이스를 구현하여
 * 결제(pay)와 취소(cancel) 로직을 캡슐화한다.
 */
public interface PaymentStrategy {

    /**
     * 결제를 실행한다.
     *
     * @param context 결제에 필요한 컨텍스트 정보
     * @return 결제 결과
     */
    PaymentResult pay(PaymentContext context);

    /**
     * 결제를 취소한다.
     *
     * @param pgTransactionId PG사 거래 키 (Y_POINT의 경우 null)
     * @param amount          취소 금액
     * @param memberId        회원 ID (Y_POINT 복구 시 필요)
     */
    void cancel(String pgTransactionId, int amount, Long memberId);
}
