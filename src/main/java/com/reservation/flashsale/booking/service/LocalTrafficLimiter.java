package com.reservation.flashsale.booking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로컬 트래픽 제어기.
 * Redis와 DB 부하를 원천 차단하기 위해 애플리케이션 메모리 레벨에서 1차로 트래픽을 방어한다.
 */
@Slf4j
@Component
public class LocalTrafficLimiter {

    // 상품별 로컬 재고 카운터
    private final Map<Long, AtomicInteger> localStockCounters = new ConcurrentHashMap<>();

    /**
     * 로컬 메모리에 상품의 초기 재고를 설정한다.
     * 주로 애플리케이션 시작 시 DB에서 전체 재고를 읽어올 때 호출된다.
     */
    public void initializeStock(Long productId, int quantity) {
        localStockCounters.put(productId, new AtomicInteger(quantity));
        log.info("[LocalLimit] 로컬 카운터 초기화: productId={}, quantity={}", productId, quantity);
    }

    /**
     * 로컬 카운터를 차감한다. (Fail-Fast 용도)
     *
     * @return 차감 후 남은 값이 0 미만이면 false (품절), 0 이상이면 true (통과)
     */
    public boolean tryAcquire(Long productId) {
        AtomicInteger counter = localStockCounters.get(productId);
        
        // 카운터가 초기화되지 않은 경우 (오류 방지)
        if (counter == null) {
            log.warn("[LocalLimit] 로컬 카운터가 초기화되지 않음: productId={}", productId);
            return true; // 일단 통과시켜 Redis/DB가 판단하게 함
        }

        int remaining = counter.decrementAndGet();
        if (remaining < 0) {
            counter.incrementAndGet(); // 음수가 되면 다시 원복하여 카운터가 무한히 내려가는 것을 방지 (Leak 방지)
            log.info("[LocalLimit] 로컬 카운터 소진 (차단): productId={}", productId);
            return false;
        }

        return true;
    }

    /**
     * 결제 실패 등의 이유로 로컬 카운터를 복구한다.
     */
    public void restore(Long productId) {
        AtomicInteger counter = localStockCounters.get(productId);
        if (counter != null) {
            counter.incrementAndGet();
            log.info("[LocalLimit] 로컬 카운터 복구: productId={}", productId);
        }
    }
}
