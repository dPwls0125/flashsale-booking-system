package com.reservation.flashsale.booking.service;

import com.reservation.flashsale.booking.entity.Booking;
import com.reservation.flashsale.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking createBooking(Long productId, Long memberId, String idempotencyKey, int totalAmount) {
        Booking booking = new Booking(memberId, productId, idempotencyKey, totalAmount);
        return bookingRepository.save(booking);
    }
}
