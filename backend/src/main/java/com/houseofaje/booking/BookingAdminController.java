package com.houseofaje.booking;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal/admin booking transitions. The payment webhook drives {@code confirm};
 * the reconciliation sweep drives {@code expire}; staff drive the rest.
 * Secured by Spring Security + JWT in the auth feature (protecting /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/bookings")
public class BookingAdminController {

    private final BookingService bookingService;
    private final BookingRepository bookings;

    public BookingAdminController(BookingService bookingService, BookingRepository bookings) {
        this.bookingService = bookingService;
        this.bookings = bookings;
    }

    @GetMapping
    public AdminBookingPage list(@RequestParam(required = false) BookingStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * safeSize;
        return bookings.findForAdminPage(status, offset, safeSize);
    }

    @PostMapping("/{id}/confirm")
    public Booking confirm(@PathVariable UUID id) {
        return bookingService.confirm(id);
    }

    @PostMapping("/{id}/complete")
    public Booking complete(@PathVariable UUID id) {
        return bookingService.complete(id);
    }

    @PostMapping("/{id}/cancel")
    public Booking cancel(@PathVariable UUID id) {
        return bookingService.cancel(id);
    }

    @PostMapping("/{id}/no-show")
    public Booking noShow(@PathVariable UUID id) {
        return bookingService.noShow(id);
    }

    @PostMapping("/{id}/expire")
    public Booking expire(@PathVariable UUID id) {
        return bookingService.expire(id);
    }

    @PostMapping("/{id}/mark-paid")
    public Booking markPaid(@PathVariable UUID id) {
        return bookingService.markDepositPaid(id);
    }
}
