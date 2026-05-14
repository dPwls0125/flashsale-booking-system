package com.reservation.flashsale.stock.repository;

import com.reservation.flashsale.stock.entity.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductId(Long productId);
}
