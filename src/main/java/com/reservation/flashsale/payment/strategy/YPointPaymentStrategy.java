package com.reservation.flashsale.payment.strategy;

import com.reservation.flashsale.member.entity.Member;
import com.reservation.flashsale.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Y포인트 결제 전략.
 * PG사를 경유하지 않고, 내부 회원 포인트를 직접 차감/복구한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YPointPaymentStrategy implements PaymentStrategy {

    private final MemberRepository memberRepository;

    @Override
    public PaymentResult pay(PaymentContext context) {
        Member member = memberRepository.findById(context.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + context.memberId()));

        try {
            member.deductPoints(context.amount());
            memberRepository.save(member);
            return PaymentResult.success(null, context.amount()); // PG 거래 키 없음
        } catch (IllegalStateException e) {
            return PaymentResult.fail("INSUFFICIENT_POINT", e.getMessage());
        }
    }

    @Override
    public void cancel(String pgTransactionId, int amount, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId));

        member.restorePoints(amount);
        memberRepository.save(member);
        log.info("Y포인트 복구 완료: memberId={}, amount={}", memberId, amount);
    }
}
