package com.reservation.flashsale.stock.service;

import com.reservation.flashsale.booking.service.LocalTrafficLimiter;
import com.reservation.flashsale.common.exception.BusinessException;
import com.reservation.flashsale.common.exception.ErrorCode;
import com.reservation.flashsale.stock.entity.ProductStock;
import com.reservation.flashsale.stock.repository.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private final StringRedisTemplate redisTemplate;
    private final ProductStockRepository productStockRepository;
    private final LocalTrafficLimiter localTrafficLimiter;

    /**
     * 재고 수량 조회.
     * Redis에서 조회하고, Redis에 값이 없으면 DB에서 직접 조회하여 반환한다.
     * (Redis에 세팅하지 않음 — 초기 로딩은 애플리케이션 시작 시점에만 수행)
     */
    public int getRemainingStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        String value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            return Integer.parseInt(value);
        }

        // Redis에 값이 없으면 DB fallback (세팅하지 않음)
        log.warn("Redis에 재고 정보 없음. DB에서 직접 조회합니다. productId={}", productId);
        ProductStock stock = productStockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보를 찾을 수 없습니다. productId=" + productId));
        return stock.getRemainingQuantity();
    }

    /**
     * 애플리케이션 시작 시 전체 상품 재고를 Redis와 Local 카운터에 로드한다.
     * SETNX를 사용하여, 이미 값이 있으면 덮어쓰지 않는다.
     */
    @Transactional(readOnly = true)
    public void initializeStock() {
        List<ProductStock> stocks = productStockRepository.findAll();
        for (ProductStock stock : stocks) {
            String key = STOCK_KEY_PREFIX + stock.getProductId();
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(stock.getRemainingQuantity()));
            if (Boolean.TRUE.equals(wasSet)) {
                log.info("Redis 재고 초기화 완료: productId={}, quantity={}", stock.getProductId(), stock.getRemainingQuantity());
            } else {
                log.info("Redis 재고 이미 존재 (SKIP): productId={}", stock.getProductId());
            }
            
            // 로컬 트래픽 제한기 초기화
            localTrafficLimiter.initializeStock(stock.getProductId(), stock.getRemainingQuantity());
        }
    }

    /**
     * Redis에서 재고를 1 차감한다. (DECR)
     * 차감 결과가 0 미만이면 품절이므로 복구(INCR)하고 false를 반환한다.
     * 결과가 0 이상이면 차감 성공이다 (0이면 마지막 남은 1개를 구매한 것).
     */
    public boolean decreaseStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key);

            if (remaining == null || remaining < 0) {
                log.info("[Redis] 재고 소진: productId={}", productId);
                // 0 미만으로 떨어지면 품절이므로 다시 증가시켜 복원
                if (remaining != null) {
                    redisTemplate.opsForValue().increment(key);
                }
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("[Redis Fallback] Redis 장애 발생. 재고 차감을 DB에 위임합니다. productId={}", productId, e);
            return true; // 무조건 통과시키고 DB의 벌크 업데이트 검증(WHERE remaining > 0)에 위임
        }
    }

    /**
     * 결제 실패 등의 이유로 Redis 재고를 복구한다. (INCR)
     */
    public void restoreStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        try {
            redisTemplate.opsForValue().increment(key);
            log.info("[Redis] 재고 복구 완료: productId={}", productId);
        } catch (Exception e) {
            log.warn("[Redis Fallback] Redis 장애 발생. 재고 복구(INCR)를 건너뜁니다. productId={}", productId, e);
        }
    }

    /**
     * 최종 예약 확정 시 DB 재고를 1 차감한다.
     */
    @Transactional
    public void decreaseDbStock(Long productId) {
        int updated = productStockRepository.decreaseStock(productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "상품이 품절되었습니다. (DB 재고 부족)");
        }
    }
}
