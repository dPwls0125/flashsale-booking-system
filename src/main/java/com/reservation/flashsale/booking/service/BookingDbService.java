package com.reservation.flashsale.booking.service;

import com.reservation.flashsale.booking.entity.Booking;
import com.reservation.flashsale.payment.entity.PaymentMethod;
import com.reservation.flashsale.payment.service.PaymentService;
import com.reservation.flashsale.payment.strategy.PaymentResult;
import com.reservation.flashsale.product.entity.Product;
import com.reservation.flashsale.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DB 관련 작업을 하나의 트랜잭션으로 묶어 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class BookingDbService {

    private final StockService stockService;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    @Transactional
    public Booking saveToDatabase(Long productId, Long memberId, Product product,
                                     List<PaymentMethod> methods, List<PaymentResult> successfulPayments,
                                     List<Integer> amounts, String idempotencyKey) {
        
        // 1. DB 재고 차감
        stockService.decreaseDbStock(productId);

        // 2. 예약 내역 생성
        Booking booking = bookingService.createBooking(productId, memberId, idempotencyKey, product.getPrice());

        // 3. 결제 내역 저장
        for (int i = 0; i < methods.size(); i++) {
            PaymentMethod method = methods.get(i);
            PaymentResult result = successfulPayments.get(i);
            int amount = amounts.get(i);
            
            paymentService.saveSuccessfulPayment(booking.getId(), method, amount, result.pgTransactionId());
        }

        // 4. 예약 확정 상태로 변경
        booking.confirm();

        return booking;
    }
}
