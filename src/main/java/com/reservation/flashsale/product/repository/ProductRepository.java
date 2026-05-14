package com.reservation.flashsale.product.repository;

import com.reservation.flashsale.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
