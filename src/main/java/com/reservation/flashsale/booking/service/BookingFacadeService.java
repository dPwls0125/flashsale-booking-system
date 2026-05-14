package com.reservation.flashsale.booking.service;

import com.reservation.flashsale.booking.dto.BookingRequest;
import com.reservation.flashsale.booking.dto.BookingResponse;
import com.reservation.flashsale.booking.entity.Booking;
import com.reservation.flashsale.booking.repository.BookingRepository;
import com.reservation.flashsale.common.exception.BusinessException;
import com.reservation.flashsale.common.exception.ErrorCode;
import com.reservation.flashsale.common.idempotency.IdempotencyService;
import com.reservation.flashsale.payment.entity.Payment;
import com.reservation.flashsale.payment.entity.PaymentMethod;
import com.reservation.flashsale.payment.repository.PaymentRepository;
import com.reservation.flashsale.payment.strategy.PaymentContext;
import com.reservation.flashsale.payment.strategy.PaymentResult;
import com.reservation.flashsale.payment.strategy.PaymentStrategy;
import com.reservation.flashsale.payment.strategy.PaymentStrategyFactory;
import com.reservation.flashsale.product.entity.Product;
import com.reservation.flashsale.product.service.ProductService;
import com.reservation.flashsale.product.service.PurchaseCheckService;
import com.reservation.flashsale.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingFacadeService {

    private final LocalTrafficLimiter localTrafficLimiter;
    private final IdempotencyService idempotencyService;
    private final PurchaseCheckService purchaseCheckService;
    private final StockService stockService;
    private final PaymentStrategyFactory paymentStrategyFactory;
    
    private final ProductService productService;
    private final BookingService bookingService;
    private final com.reservation.flashsale.payment.service.PaymentService paymentService;

    /**
     * 전체 예약 흐름 오케스트레이션
     */
    public BookingResponse book(Long productId, Long memberId, String idempotencyKey, BookingRequest request) {
        
        // 1. 로컬 트래픽 제한 (Fail-Fast)
        if (!localTrafficLimiter.tryAcquire(productId)) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "상품이 품절되었습니다. (로컬 차단)");
        }

        // 보상 트랜잭션을 위한 상태 플래그
        boolean idempotencySet = false;
        boolean purchaseChecked = false;
        boolean stockDecreased = false;
        List<PaymentResult> successfulPayments = new ArrayList<>();
        List<PaymentMethod> executedMethods = new ArrayList<>();

        try {
            // 2. 멱등성 체크
            if (!idempotencyService.checkAndSetProcessing(idempotencyKey)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            idempotencySet = true;

            // 3. 1인 1건 체크
            if (!purchaseCheckService.checkAndAdd(productId, memberId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_BOOKING);
            }
            purchaseChecked = true;

            // 4. Redis 재고 차감
            if (!stockService.decreaseStock(productId)) {
                throw new BusinessException(ErrorCode.SOLD_OUT, "상품이 품절되었습니다. (Redis 재고 부족)");
            }
            stockDecreased = true;

            // 상품 및 가격 정보 조회
            Product product = productService.getProduct(productId);
            
            // 결제 금액 배분 (과제 편의상 1개 수단일 때는 전액, 복합일 때는 1/N 등 단순화하거나 첫번째 수단으로 전액 처리)
            // 여기서는 1개 수단 사용 또는 복합결제의 경우 균등 분할로 가정
            List<PaymentMethod> methods = request.paymentMethods();
            paymentStrategyFactory.validateCombination(methods);
            
            int amountPerMethod = product.getPrice() / methods.size();
            String orderId = UUID.randomUUID().toString();

            // 5. 결제 처리 (전략 패턴)
            for (PaymentMethod method : methods) {
                PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
                PaymentContext context = new PaymentContext(orderId, product.getName(), amountPerMethod, memberId);
                
                PaymentResult result = strategy.pay(context);
                if (!result.success()) {
                    log.error("결제 실패: 수단={}, 에러={}", method, result.errorMessage());
                    throw new BusinessException(ErrorCode.PAYMENT_FAILED, "결제 실패: " + result.errorMessage());
                }
                
                successfulPayments.add(result);
                executedMethods.add(method);
            }

            // 6. DB 동기화 (트랜잭션)
            Booking booking = saveToDatabase(productId, memberId, product, methods, successfulPayments, amountPerMethod, idempotencyKey);
            
            // 7. 멱등성 키 상태 완료
            idempotencyService.markCompleted(idempotencyKey);
            
            return new BookingResponse(
                    booking.getId(),
                    productId,
                    product.getName(),
                    product.getPrice(),
                    "SUCCESS"
            );

        } catch (Exception e) {
            log.error("예약 처리 중 예외 발생, 보상 트랜잭션 시작: {}", e.getMessage());
            
            // 보상 트랜잭션 (역순 복구)
            executeCompensation(productId, memberId, idempotencyKey, executedMethods, successfulPayments, 
                                idempotencySet, purchaseChecked, stockDecreased);
            
            throw e; // 사용자에게 원래 예외 반환
        }
    }

    @Transactional
    protected Booking saveToDatabase(Long productId, Long memberId, Product product, 
                                     List<PaymentMethod> methods, List<PaymentResult> successfulPayments, 
                                     int amountPerMethod, String idempotencyKey) {
        
        // ProductStock Update (DB)
        stockService.decreaseDbStock(productId);

        // Booking Insert
        Booking booking = bookingService.createBooking(productId, memberId, idempotencyKey, product.getPrice());

        // Payment Insert
        for (int i = 0; i < methods.size(); i++) {
            PaymentMethod method = methods.get(i);
            PaymentResult result = successfulPayments.get(i);
            
            paymentService.saveSuccessfulPayment(booking.getId(), method, amountPerMethod, result.pgTransactionId());
        }

        return booking;
    }

    /**
     * 보상 트랜잭션 실행 (역순)
     */
    private void executeCompensation(Long productId, Long memberId, String idempotencyKey,
                                     List<PaymentMethod> executedMethods, List<PaymentResult> successfulPayments,
                                     boolean idempotencySet, boolean purchaseChecked, boolean stockDecreased) {
        // 1. 이미 성공한 결제 취소
        for (int i = executedMethods.size() - 1; i >= 0; i--) {
            try {
                PaymentMethod method = executedMethods.get(i);
                PaymentResult result = successfulPayments.get(i);
                PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
                
                strategy.cancel(result.pgTransactionId(), result.amount() /* <- PaymentResult에 amount 미포함 이슈 수정 필요 */, memberId);
                log.info("결제 취소 보상 트랜잭션 성공: 수단={}", method);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(결제 취소) 실패 - 수동 복구 필요: {}", ex.getMessage());
            }
        }

        // 2. 재고 복구
        if (stockDecreased) {
            try {
                stockService.restoreStock(productId);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(재고 복구) 실패: {}", ex.getMessage());
            }
        }

        // 3. 1인 1건 내역 삭제
        if (purchaseChecked) {
            try {
                purchaseCheckService.remove(productId, memberId);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(구매 이력 삭제) 실패: {}", ex.getMessage());
            }
        }

        // 4. 멱등성 키 삭제 (재시도 허용)
        if (idempotencySet) {
            try {
                idempotencyService.deleteKey(idempotencyKey);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(멱등성 키 삭제) 실패: {}", ex.getMessage());
            }
        }

        // 5. 로컬 카운터 복구
        try {
            localTrafficLimiter.restore(productId);
        } catch (Exception ex) {
            log.error("보상 트랜잭션(로컬 카운터 복구) 실패: {}", ex.getMessage());
        }
    }
}
