package com.reservation.flashsale.payment.strategy;

import com.reservation.flashsale.payment.pg.PgClient;
import com.reservation.flashsale.payment.pg.PgCancelResponse;
import com.reservation.flashsale.payment.pg.PgPaymentRequest;
import com.reservation.flashsale.payment.pg.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 신용카드 결제 전략.
 * PG사를 통해 카드 결제 승인 및 취소를 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private final PgClient pgClient;

    @Override
    public PaymentResult pay(PaymentContext context) {
        PgPaymentRequest request = new PgPaymentRequest(
                context.orderId(), context.orderName(), context.amount());

        PgPaymentResponse response = pgClient.confirm(request);

        if (response.isSuccess()) {
            return PaymentResult.success(response.paymentKey());
        }

        return PaymentResult.fail(
                response.failure().code(),
                response.failure().message());
    }

    @Override
    public void cancel(String pgTransactionId, int amount, Long memberId) {
        PgCancelResponse response = pgClient.cancel(pgTransactionId, "예약 취소", amount);
        if (!response.isSuccess()) {
            log.error("신용카드 결제 취소 실패: paymentKey={}, error={}",
                    pgTransactionId, response.failure());
            throw new IllegalStateException("신용카드 결제 취소 실패");
        }
    }
}
