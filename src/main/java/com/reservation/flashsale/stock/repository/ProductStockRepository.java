package com.reservation.flashsale.stock.repository;

import com.reservation.flashsale.stock.entity.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductId(Long productId);

    /**
     * DB에서 재고를 1 차감한다. (JPA 더티체킹 대신 동시성 이슈를 피하기 위한 벌크 업데이트)
     * remainingQuantity > 0 조건으로 최후의 DB 레벨 동시성 방어를 수행한다.
     * 
     * @return 업데이트된 행의 수 (성공 시 1, 재고 부족 시 0)
     */
    @Modifying
    @Query("UPDATE ProductStock p SET p.remainingQuantity = p.remainingQuantity - 1 WHERE p.productId = :productId AND p.remainingQuantity > 0")
    int decreaseStock(Long productId);
}
