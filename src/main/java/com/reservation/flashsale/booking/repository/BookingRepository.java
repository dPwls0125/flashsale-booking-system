package com.reservation.flashsale.booking.repository;

import com.reservation.flashsale.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
