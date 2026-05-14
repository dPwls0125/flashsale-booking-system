package com.reservation.flashsale.payment.strategy;

import com.reservation.flashsale.common.exception.BusinessException;
import com.reservation.flashsale.common.exception.ErrorCode;
import com.reservation.flashsale.payment.entity.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 결제 수단에 따라 적절한 PaymentStrategy를 반환하는 팩토리.
 * 복합 결제 시 결제 수단 조합의 유효성도 검증한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final CreditCardPaymentStrategy creditCardStrategy;
    private final YPayPaymentStrategy yPayStrategy;
    private final YPointPaymentStrategy yPointStrategy;

    private static final Map<PaymentMethod, List<PaymentMethod>> INCOMPATIBLE_COMBINATIONS = Map.of(
            PaymentMethod.CREDIT_CARD, List.of(PaymentMethod.Y_PAY),
            PaymentMethod.Y_PAY, List.of(PaymentMethod.CREDIT_CARD)
    );

    public PaymentStrategy getStrategy(PaymentMethod method) {
        return switch (method) {
            case CREDIT_CARD -> creditCardStrategy;
            case Y_PAY -> yPayStrategy;
            case Y_POINT -> yPointStrategy;
        };
    }

    /**
     * 복합 결제 시 결제 수단 조합의 유효성을 검증한다.
     * 신용카드 + Y페이 혼용 불가.
     *
     * @throws IllegalArgumentException 유효하지 않은 조합인 경우
     */
    public void validateCombination(List<PaymentMethod> methods) {
        for (PaymentMethod method : methods) {
            List<PaymentMethod> incompatible = INCOMPATIBLE_COMBINATIONS.get(method);
            if (incompatible != null) {
                for (PaymentMethod other : methods) {
                    if (incompatible.contains(other)) {
                        throw new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION, 
                                "결제 수단 조합이 유효하지 않습니다: " + method + "와 " + other + "는 함께 사용할 수 없습니다.");
                    }
                }
            }
        }
    }
}
