package com.reservation.flashsale.booking.controller;

import com.reservation.flashsale.booking.dto.BookingRequest;
import com.reservation.flashsale.booking.dto.BookingResponse;
import com.reservation.flashsale.booking.service.BookingFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacadeService bookingFacadeService;

    @PostMapping("/{productId}")
    public ResponseEntity<BookingResponse> createBooking(
            @PathVariable Long productId,
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody BookingRequest request) {

        BookingResponse response = bookingFacadeService.book(productId, memberId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
