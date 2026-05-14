package com.reservation.flashsale.payment.service;

import com.reservation.flashsale.payment.entity.Payment;
import com.reservation.flashsale.payment.entity.PaymentMethod;
import com.reservation.flashsale.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment saveSuccessfulPayment(Long bookingId, PaymentMethod method, int amount, String pgTransactionId) {
        Payment payment = new Payment(bookingId, method, amount);
        payment.markSuccess(pgTransactionId);
        return paymentRepository.save(payment);
    }
}
