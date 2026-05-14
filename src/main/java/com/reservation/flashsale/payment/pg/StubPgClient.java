package com.reservation.flashsale.payment.pg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PG사 Stub 구현체.
 * 실제 PG 연동 없이 결제 승인/취소를 시뮬레이션한다.
 *
 * <p>테스트를 위해 특정 금액으로 다양한 PG 실패 시나리오를 재현할 수 있다.</p>
 * <ul>
 *   <li>금액 끝자리 10원 → 카드사 거절 (REJECT_CARD_COMPANY)</li>
 *   <li>금액 끝자리 20원 → 한도 초과 (EXCEED_MAX_AMOUNT)</li>
 *   <li>금액 끝자리 30원 → 카드 정보 오류 (INVALID_CARD_NUMBER)</li>
 *   <li>금액 끝자리 40원 → PG사 내부 오류 (PROVIDER_ERROR)</li>
 *   <li>금액 끝자리 50원 → 네트워크 타임아웃 (RuntimeException)</li>
 *   <li>그 외 → 정상 승인</li>
 * </ul>
 */
@Slf4j
@Component
public class StubPgClient implements PgClient {

    @Override
    public PgPaymentResponse confirm(PgPaymentRequest request) {
        log.info("[StubPG] 결제 승인 요청: orderId={}, amount={}", request.orderId(), request.amount());

        int trigger = request.amount() % 100;

        return switch (trigger) {
            case 10 -> {
                log.warn("[StubPG] 카드사 거절 시뮬레이션");
                yield PgPaymentResponse.fail(request.orderId(),
                        "REJECT_CARD_COMPANY", "카드사에서 결제를 거절했습니다. 카드사에 문의해주세요.");
            }
            case 20 -> {
                log.warn("[StubPG] 한도 초과 시뮬레이션");
                yield PgPaymentResponse.fail(request.orderId(),
                        "EXCEED_MAX_AMOUNT", "결제 금액이 카드 한도를 초과했습니다.");
            }
            case 30 -> {
                log.warn("[StubPG] 카드 정보 오류 시뮬레이션");
                yield PgPaymentResponse.fail(request.orderId(),
                        "INVALID_CARD_NUMBER", "유효하지 않은 카드 번호입니다.");
            }
            case 40 -> {
                log.warn("[StubPG] PG사 내부 오류 시뮬레이션");
                yield PgPaymentResponse.fail(request.orderId(),
                        "PROVIDER_ERROR", "PG사에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }
            case 50 -> {
                log.warn("[StubPG] 네트워크 타임아웃 시뮬레이션");
                throw new RuntimeException("[StubPG] PG사 응답 타임아웃 (시뮬레이션)");
            }
            default -> {
                String paymentKey = "tpay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                log.info("[StubPG] 결제 승인 성공: paymentKey={}", paymentKey);
                yield PgPaymentResponse.success(paymentKey, request.orderId(), request.amount(), request.orderName());
            }
        };
    }

    @Override
    public PgCancelResponse cancel(String paymentKey, String cancelReason, int cancelAmount) {
        log.info("[StubPG] 결제 취소 요청: paymentKey={}, amount={}, reason={}",
                paymentKey, cancelAmount, cancelReason);

        String transactionKey = UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
        log.info("[StubPG] 결제 취소 성공: transactionKey={}", transactionKey);

        return PgCancelResponse.success(paymentKey, transactionKey, cancelAmount, cancelReason);
    }
}
