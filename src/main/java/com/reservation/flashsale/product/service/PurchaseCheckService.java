package com.reservation.flashsale.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 1인 1건 구매 정책 검증 서비스.
 * Redis의 Set 자료구조(SADD, SREM)를 활용하여 원자적으로 검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseCheckService {

    private static final String PURCHASED_KEY_PREFIX = "purchased:product:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 회원이 이미 해당 상품을 구매했는지 확인하고, 안 했으면 Set에 추가한다. (SADD)
     *
     * @return Set에 성공적으로 추가되었으면 true (첫 구매), 이미 존재하면 false (중복 구매)
     */
    public boolean checkAndAdd(Long productId, Long memberId) {
        String key = PURCHASED_KEY_PREFIX + productId;
        try {
            // SADD는 Set에 추가 성공 시 1, 이미 존재하면 0을 반환함
            Long result = redisTemplate.opsForSet().add(key, String.valueOf(memberId));
            
            if (result != null && result > 0) {
                log.info("[PurchaseCheck] 1인 1건 통과 (추가됨): productId={}, memberId={}", productId, memberId);
                return true;
            }

            log.warn("[PurchaseCheck] 이미 구매 이력 존재 (차단): productId={}, memberId={}", productId, memberId);
            return false;
        } catch (Exception e) {
            log.warn("[Redis Fallback] Redis 장애 발생. 1인 1매 검증을 DB Unique Constraint로 우회합니다. productId={}, memberId={}", productId, memberId, e);
            return true; // 에러 무시하고 통과시킴 (DB 에러가 차단할 것)
        }
    }

    /**
     * 결제 실패 등의 이유로 예약을 롤백할 때 구매 이력을 삭제한다. (SREM)
     */
    public void remove(Long productId, Long memberId) {
        String key = PURCHASED_KEY_PREFIX + productId;
        redisTemplate.opsForSet().remove(key, String.valueOf(memberId));
        log.info("[PurchaseCheck] 구매 이력 삭제 (롤백): productId={}, memberId={}", productId, memberId);
    }
}
