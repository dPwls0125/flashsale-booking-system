package com.reservation.flashsale.product.controller;

import com.reservation.flashsale.member.entity.Member;
import com.reservation.flashsale.member.service.MemberService;
import com.reservation.flashsale.product.dto.CheckoutResponse;
import com.reservation.flashsale.product.entity.Product;
import com.reservation.flashsale.product.service.ProductService;
import com.reservation.flashsale.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {

    private final ProductService productService;
    private final StockService stockService;
    private final MemberService memberService;

    /**
     * 주문서 진입 API.
     * 상품 정보(Caffeine 캐시) + 재고 확인(Redis GET) + 사용자 포인트(DB)를 조합하여 응답한다.
     *
     * @param productId 상품 ID
     * @param memberId  회원 ID (헤더로 전달, 실제 환경에서는 인증 토큰에서 추출)
     */
    @GetMapping("/checkout/{productId}")
    public ResponseEntity<CheckoutResponse> checkout(
            @PathVariable Long productId,
            @RequestHeader("X-Member-Id") Long memberId) {

        Product product = productService.getProduct(productId);
        int remainingStock = stockService.getRemainingStock(productId);
        Member member = memberService.getMember(memberId);

        CheckoutResponse response = CheckoutResponse.of(product, remainingStock, member.getPointBalance());

        return ResponseEntity.ok(response);
    }
}
