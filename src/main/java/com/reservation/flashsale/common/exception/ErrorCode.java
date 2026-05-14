package com.reservation.flashsale.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 상품 관련
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "상품을 찾을 수 없습니다."),
    SOLD_OUT(HttpStatus.CONFLICT, "P002", "상품이 품절되었습니다."),

    // 예약 관련
    DUPLICATE_BOOKING(HttpStatus.CONFLICT, "B001", "이미 이 상품을 구매하셨습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "B002", "이미 처리 중이거나 완료된 주문입니다."),
    
    // 결제 관련
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAY001", "결제에 실패했습니다."),
    INVALID_PAYMENT_COMBINATION(HttpStatus.BAD_REQUEST, "PAY002", "결제 수단 조합이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
