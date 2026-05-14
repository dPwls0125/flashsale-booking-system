package com.reservation.flashsale.payment.repository;

import com.reservation.flashsale.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
