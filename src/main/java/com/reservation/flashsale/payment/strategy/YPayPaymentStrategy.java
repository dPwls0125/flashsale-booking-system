package com.reservation.flashsale.payment.strategy;

import com.reservation.flashsale.payment.pg.PgClient;
import com.reservation.flashsale.payment.pg.PgCancelResponse;
import com.reservation.flashsale.payment.pg.PgPaymentRequest;
import com.reservation.flashsale.payment.pg.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Y페이 결제 전략.
 * PG사를 통해 Y페이 결제 승인 및 취소를 처리한다.
 * 신용카드와 동일하게 PG를 경유하지만, 결제 수단 구분을 위해 별도 전략으로 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YPayPaymentStrategy implements PaymentStrategy {

    private final PgClient pgClient;

    @Override
    public PaymentResult pay(PaymentContext context) {
        PgPaymentRequest request = new PgPaymentRequest(
                context.orderId(), context.orderName(), context.amount());

        PgPaymentResponse response = pgClient.confirm(request);

        if (response.isSuccess()) {
            return PaymentResult.success(response.paymentKey(), context.amount());
        }

        return PaymentResult.fail(
                response.failure().code(),
                response.failure().message());
    }

    @Override
    public void cancel(String pgTransactionId, int amount, Long memberId) {
        PgCancelResponse response = pgClient.cancel(pgTransactionId, "예약 취소", amount);
        if (!response.isSuccess()) {
            log.error("Y페이 결제 취소 실패: paymentKey={}, error={}",
                    pgTransactionId, response.failure());
            throw new IllegalStateException("Y페이 결제 취소 실패");
        }
    }
}
