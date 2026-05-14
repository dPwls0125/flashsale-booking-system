package com.reservation.flashsale.booking.service;

import com.reservation.flashsale.booking.dto.BookingRequest;
import com.reservation.flashsale.booking.dto.BookingResponse;
import com.reservation.flashsale.booking.entity.Booking;
import com.reservation.flashsale.common.exception.BusinessException;
import com.reservation.flashsale.common.exception.ErrorCode;
import com.reservation.flashsale.common.idempotency.IdempotencyService;
import com.reservation.flashsale.payment.entity.PaymentMethod;
import com.reservation.flashsale.payment.strategy.*;
import com.reservation.flashsale.product.entity.Product;
import com.reservation.flashsale.product.service.ProductService;
import com.reservation.flashsale.product.service.PurchaseCheckService;
import com.reservation.flashsale.stock.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingFacadeServiceTest {

    @InjectMocks
    private BookingFacadeService bookingFacadeService;

    @Mock private LocalTrafficLimiter localTrafficLimiter;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PurchaseCheckService purchaseCheckService;
    @Mock private StockService stockService;
    @Mock private PaymentStrategyFactory paymentStrategyFactory;
    @Mock private ProductService productService;
    @Mock private BookingDbService bookingDbService;
    @Mock private PaymentStrategy paymentStrategy;

    private final Long productId = 1L;
    private final Long memberId = 100L;
    private final String idempotencyKey = "test-key";

    @Test
    @DisplayName("정상 예약 흐름: 모든 단계 성공 시 SUCCESS 응답 반환")
    void book_success() {
        // given
        BookingRequest request = new BookingRequest(List.of(PaymentMethod.CREDIT_CARD));
        
        // Product 생성 (ID는 리플렉션으로 설정)
        Product product = new Product("상품A", 10000, java.time.LocalTime.of(15, 0), java.time.LocalTime.of(11, 0), "설명");
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        
        // Booking 생성 (ID는 리플렉션으로 설정)
        Booking booking = new Booking(memberId, productId, idempotencyKey, 10000);
        org.springframework.test.util.ReflectionTestUtils.setField(booking, "id", 1L);

        given(localTrafficLimiter.tryAcquire(productId)).willReturn(true);
        given(idempotencyService.checkAndSetProcessing(idempotencyKey)).willReturn(true);
        given(purchaseCheckService.checkAndAdd(productId, memberId)).willReturn(true);
        given(stockService.decreaseStock(productId)).willReturn(true);
        given(productService.getProduct(productId)).willReturn(product);
        given(paymentStrategyFactory.getStrategy(any())).willReturn(paymentStrategy);
        given(paymentStrategy.pay(any())).willReturn(PaymentResult.success("pg-tid", 10000));
        given(bookingDbService.saveToDatabase(any(), any(), any(), any(), any(), any(), any())).willReturn(booking);

        // when
        BookingResponse response = bookingFacadeService.book(productId, memberId, idempotencyKey, request);

        // then
        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(idempotencyService).markCompleted(idempotencyKey);
    }

    @Test
    @DisplayName("결제 실패 시 보상 트랜잭션(재고 복구, 구매이력 삭제 등)이 호출되어야 한다")
    void book_payment_fail_compensation() {
        // given
        BookingRequest request = new BookingRequest(List.of(PaymentMethod.CREDIT_CARD));
        Product product = new Product("상품A", 10000, java.time.LocalTime.of(15, 0), java.time.LocalTime.of(11, 0), "설명");
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);

        given(localTrafficLimiter.tryAcquire(productId)).willReturn(true);
        given(idempotencyService.checkAndSetProcessing(idempotencyKey)).willReturn(true);
        given(purchaseCheckService.checkAndAdd(productId, memberId)).willReturn(true);
        given(stockService.decreaseStock(productId)).willReturn(true);
        given(productService.getProduct(productId)).willReturn(product);
        given(paymentStrategyFactory.getStrategy(any())).willReturn(paymentStrategy);
        
        // 결제 실패 설정
        given(paymentStrategy.pay(any())).willReturn(PaymentResult.fail("F001", "한도 초과"));

        // when & then
        assertThatThrownBy(() -> bookingFacadeService.book(productId, memberId, idempotencyKey, request))
                .isInstanceOf(BusinessException.class);

        // 보상 트랜잭션 검증
        verify(stockService).restoreStock(productId);
        verify(purchaseCheckService).remove(productId, memberId);
        verify(idempotencyService).deleteKey(idempotencyKey);
        verify(localTrafficLimiter).restore(productId);
    }

    @Test
    @DisplayName("멱등성 체크 실패 시 즉시 예외가 발생하고 이후 단계는 진행되지 않는다")
    void book_idempotency_conflict() {
        // given
        BookingRequest request = new BookingRequest(List.of(PaymentMethod.CREDIT_CARD));
        given(localTrafficLimiter.tryAcquire(productId)).willReturn(true);
        given(idempotencyService.checkAndSetProcessing(idempotencyKey)).willReturn(false); // 이미 처리 중

        // when & then
        assertThatThrownBy(() -> bookingFacadeService.book(productId, memberId, idempotencyKey, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);

        verify(purchaseCheckService, never()).checkAndAdd(any(), any());
        verify(stockService, never()).decreaseStock(any());
    }

    @Test
    @DisplayName("로컬 트래픽 차단 시 Redis/DB 접근 없이 즉시 품절 예외가 발생한다")
    void book_local_blocked() {
        // given
        BookingRequest request = new BookingRequest(List.of(PaymentMethod.CREDIT_CARD));
        given(localTrafficLimiter.tryAcquire(productId)).willReturn(false); // 로컬 차단

        // when & then
        assertThatThrownBy(() -> bookingFacadeService.book(productId, memberId, idempotencyKey, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOLD_OUT);

        verify(idempotencyService, never()).checkAndSetProcessing(any());
        verify(stockService, never()).decreaseStock(any());
    }
    @Test
    @DisplayName("복합 결제 시 금액이 정확히 배분되어야 하며, 합계가 상품 가격과 일치해야 한다")
    void book_multiple_payments_rounding_check() {
        // given
        // 10000원을 3개 수단으로 결제 시: 3333, 3333, 3334원이어야 함
        BookingRequest request = new BookingRequest(List.of(PaymentMethod.CREDIT_CARD, PaymentMethod.Y_PAY, PaymentMethod.Y_POINT));
        Product product = new Product("상품A", 10000, java.time.LocalTime.of(15, 0), java.time.LocalTime.of(11, 0), "설명");
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        
        Booking booking = new Booking(memberId, productId, idempotencyKey, 10000);

        given(localTrafficLimiter.tryAcquire(productId)).willReturn(true);
        given(idempotencyService.checkAndSetProcessing(idempotencyKey)).willReturn(true);
        given(purchaseCheckService.checkAndAdd(productId, memberId)).willReturn(true);
        given(stockService.decreaseStock(productId)).willReturn(true);
        given(productService.getProduct(productId)).willReturn(product);
        given(paymentStrategyFactory.getStrategy(any())).willReturn(paymentStrategy);
        given(paymentStrategy.pay(any())).willReturn(PaymentResult.success("pg-tid", 3333));
        given(bookingDbService.saveToDatabase(any(), any(), any(), any(), any(), any(), any())).willReturn(booking);

        // when
        bookingFacadeService.book(productId, memberId, idempotencyKey, request);

        // then
        // saveToDatabase에 전달된 금액 리스트 검증
        verify(bookingDbService).saveToDatabase(
                eq(productId), eq(memberId), eq(product), any(), any(), 
                argThat(amounts -> amounts.get(0) == 3333 && amounts.get(1) == 3333 && amounts.get(2) == 3334),
                eq(idempotencyKey)
        );
    }
}
