package com.reservation.flashsale.product.service;

import com.reservation.flashsale.product.entity.Product;
import com.reservation.flashsale.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 정보 조회.
     * Caffeine 로컬 캐시(30초 TTL) → DB 조회 순으로 동작한다.
     * 상품 정보는 변경 빈도가 낮으므로 로컬 캐시로 DB 부하를 줄인다.
     */
    @Cacheable(value = "product", key = "#productId")
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + productId));
    }
}
