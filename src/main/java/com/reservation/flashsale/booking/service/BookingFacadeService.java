package com.reservation.flashsale.booking.service;

import com.reservation.flashsale.booking.dto.BookingRequest;
import com.reservation.flashsale.booking.dto.BookingResponse;
import com.reservation.flashsale.booking.entity.Booking;
import com.reservation.flashsale.common.exception.BusinessException;
import com.reservation.flashsale.common.exception.ErrorCode;
import com.reservation.flashsale.common.idempotency.IdempotencyService;
import com.reservation.flashsale.member.entity.Member;
import com.reservation.flashsale.member.service.MemberService;
import com.reservation.flashsale.payment.entity.PaymentMethod;
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
    private final MemberService memberService;
    private final BookingDbService bookingDbService;

    /**
     * 주문서 진입 시 필요한 정보(상품, 포인트)를 조회한다. (GET Checkout API)
     * 선착순 로직이 필요 없는 단순 조회용이다.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BookingResponse getCheckoutInfo(Long productId, Long memberId) {
        Product product = productService.getProduct(productId);
        Member member = memberService.getMember(memberId);
        int remainingStock = stockService.getRemainingStock(productId);

        return BookingResponse.success(
                null, // 아직 예약 전
                productId,
                product.getName(),
                product.getPrice(),
                product.getCheckInTime(),
                product.getCheckOutTime(),
                member.getPointBalance(),
                remainingStock
        );
    }

    /**
     * 전체 예약 흐름 오케스트레이션 (POST Booking API)
     */
    public BookingResponse book(Long productId, Long memberId, String idempotencyKey, BookingRequest request) {
        
        // 보상 트랜잭션을 위한 상태 플래그
        boolean localAcquired = false;
        boolean idempotencySet = false;
        boolean purchaseChecked = false;
        boolean stockDecreased = false;
        List<PaymentResult> successfulPayments = new ArrayList<>();
        List<PaymentMethod> executedMethods = new ArrayList<>();

        try {
            // 1. 로컬 트래픽 제한 (Redis 부하 감소 핵심 로직)
            if (!localTrafficLimiter.tryAcquire(productId)) {
                throw new BusinessException(ErrorCode.SOLD_OUT, "상품이 품절되었습니다. (로컬 차단)");
            }
            localAcquired = true;

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

            // 4. Redis 재고 차감 (실제 선착순 경쟁)
            if (!stockService.decreaseStock(productId)) {
                throw new BusinessException(ErrorCode.SOLD_OUT, "상품이 품절되었습니다. (Redis 재고 부족)");
            }
            stockDecreased = true;

            // 상품 및 가격 정보 조회
            Product product = productService.getProduct(productId);
            
            // 결제 수단 검증 및 금액 배분
            List<PaymentMethod> methods = request.paymentMethods();
            paymentStrategyFactory.validateCombination(methods);
            
            int totalPrice = product.getPrice();
            List<Integer> amounts = new ArrayList<>();
            int sum = 0;
            for (int i = 0; i < methods.size() - 1; i++) {
                int amt = totalPrice / methods.size();
                amounts.add(amt);
                sum += amt;
            }
            amounts.add(totalPrice - sum); 
            
            String orderId = UUID.randomUUID().toString();

            // 5. 결제 처리
            for (int i = 0; i < methods.size(); i++) {
                PaymentMethod method = methods.get(i);
                int amount = amounts.get(i);
                
                PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
                PaymentContext context = new PaymentContext(orderId, product.getName(), amount, memberId);
                
                PaymentResult result = strategy.pay(context);
                if (!result.success()) {
                    log.error("결제 실패: 수단={}, 에러={}", method, result.errorMessage());
                    throw new BusinessException(ErrorCode.PAYMENT_FAILED, "결제 실패: " + result.errorMessage());
                }
                
                successfulPayments.add(result);
                executedMethods.add(method);
            }

            // 6. DB 동기화 (트랜잭션 위임)
            Booking booking = bookingDbService.saveToDatabase(productId, memberId, product, methods, successfulPayments, amounts, idempotencyKey);
            
            // 7. 멱등성 키 상태 완료
            idempotencyService.markCompleted(idempotencyKey);
            
            return BookingResponse.success(
                    booking.getId(),
                    productId,
                    product.getName(),
                    product.getPrice()
            );

        } catch (Exception e) {
            log.error("예약 처리 중 예외 발생, 보상 트랜잭션 시작: {}", e.getMessage());
            
            // 보상 트랜잭션 (역순 복구)
            executeCompensation(productId, memberId, idempotencyKey, executedMethods, successfulPayments, 
                                localAcquired, idempotencySet, purchaseChecked, stockDecreased);
            
            throw e;
        }
    }

    private void executeCompensation(Long productId, Long memberId, String idempotencyKey,
                                     List<PaymentMethod> executedMethods, List<PaymentResult> successfulPayments,
                                     boolean localAcquired, boolean idempotencySet, boolean purchaseChecked, boolean stockDecreased) {
        // 1. 이미 성공한 결제 취소
        for (int i = executedMethods.size() - 1; i >= 0; i--) {
            try {
                PaymentMethod method = executedMethods.get(i);
                PaymentResult result = successfulPayments.get(i);
                PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
                
                strategy.cancel(result.pgTransactionId(), result.amount(), memberId);
                log.info("결제 취소 보상 트랜잭션 성공: 수단={}", method);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(결제 취소) 실패: {}", ex.getMessage());
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

        // 4. 멱등성 키 삭제
        if (idempotencySet) {
            try {
                idempotencyService.deleteKey(idempotencyKey);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(멱등성 키 삭제) 실패: {}", ex.getMessage());
            }
        }

        // 5. 로컬 카운터 복구
        if (localAcquired) {
            try {
                localTrafficLimiter.restore(productId);
            } catch (Exception ex) {
                log.error("보상 트랜잭션(로컬 카운터 복구) 실패: {}", ex.getMessage());
            }
        }
    }
}
