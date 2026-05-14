package com.reservation.flashsale.stock.service;

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
     * 애플리케이션 시작 시 전체 상품 재고를 Redis에 로드한다.
     * SETNX를 사용하여, 이미 값이 있으면 덮어쓰지 않는다.
     */
    @Transactional(readOnly = true)
    public void initializeStock() {
        List<ProductStock> stocks = productStockRepository.findAll();
        for (ProductStock stock : stocks) {
            String key = STOCK_KEY_PREFIX + stock.getProductId();
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(stock.getRemainingQuantity()));
            if (Boolean.TRUE.equals(wasSet)) {
                log.info("재고 초기화 완료: productId={}, quantity={}", stock.getProductId(), stock.getRemainingQuantity());
            } else {
                log.info("재고 이미 존재 (SKIP): productId={}", stock.getProductId());
            }
        }
    }
}
