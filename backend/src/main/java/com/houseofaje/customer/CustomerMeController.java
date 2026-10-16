package com.houseofaje.customer;

import com.houseofaje.booking.Booking;
import com.houseofaje.booking.BookingNotFoundException;
import com.houseofaje.booking.BookingRepository;
import com.houseofaje.booking.BookingResult;
import com.houseofaje.booking.BookingService;
import com.houseofaje.booking.BookingSummary;
import com.houseofaje.booking.CreateBookingRequest;
import com.houseofaje.booking.CustomerBookingRequest;
import com.houseofaje.notification.CustomerNotification;
import com.houseofaje.notification.CustomerNotificationRepository;
import com.houseofaje.payment.DepositInstructions;
import com.houseofaje.review.CreateReviewRequest;
import com.houseofaje.review.Review;
import com.houseofaje.review.ReviewService;
import com.houseofaje.review.SubmitReviewRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in customer's own account view. */
@RestController
@RequestMapping("/api/customer")
public class CustomerMeController {

    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final ReviewService reviews;
    private final BookingService bookingService;
    private final CustomerNotificationRepository notifications;

    public CustomerMeController(CustomerRepository customers, BookingRepository bookings, ReviewService reviews,
            BookingService bookingService, CustomerNotificationRepository notifications) {
        this.customers = customers;
        this.bookings = bookings;
        this.reviews = reviews;
        this.bookingService = bookingService;
        this.notifications = notifications;
    }

    @GetMapping("/me")
    public Customer me(Authentication auth) {
        UUID id = UUID.fromString(auth.getName());
        return customers.findById(id).orElseThrow(() -> new IllegalArgumentException("Customer not found"));
    }

    @GetMapping("/bookings")
    public List<BookingSummary> bookings(Authentication auth) {
        return bookings.findByCustomer(UUID.fromString(auth.getName()));
    }

    /** A booking placed by the logged-in customer; the customer is the session, not the body. */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResult createBooking(Authentication auth,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CustomerBookingRequest body) {
        UUID customerId = UUID.fromString(auth.getName());
        return bookingService.create(
                new CreateBookingRequest(customerId, body.serviceId(), body.staffId(), body.startAt(), body.payFull()),
                idempotencyKey);
    }

    /** Cancels one of the customer's own bookings (refund rules apply on the server). */
    @PostMapping("/bookings/{bookingId}/cancel")
    public Booking cancel(Authentication auth, @PathVariable UUID bookingId) {
        UUID customerId = UUID.fromString(auth.getName());
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.customerId().equals(customerId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return bookingService.cancel(bookingId);
    }

    /** Starts in-app payment of the remaining balance on one of the customer's bookings. */
    @PostMapping("/bookings/{bookingId}/pay-balance")
    public DepositInstructions payBalance(Authentication auth, @PathVariable UUID bookingId) {
        UUID customerId = UUID.fromString(auth.getName());
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.customerId().equals(customerId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return bookingService.payBalance(bookingId);
    }

    /** Re-issues bank-transfer instructions for a booking still awaiting deposit/full payment. */
    @PostMapping("/bookings/{bookingId}/pay-deposit")
    public DepositInstructions payDeposit(Authentication auth, @PathVariable UUID bookingId) {
        UUID customerId = UUID.fromString(auth.getName());
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.customerId().equals(customerId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return bookingService.payDeposit(bookingId);
    }

    @GetMapping("/notifications")
    public List<CustomerNotification> notifications(Authentication auth) {
        return notifications.findByCustomer(UUID.fromString(auth.getName()), 50);
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationRead(Authentication auth, @PathVariable long id) {
        notifications.markRead(id, UUID.fromString(auth.getName()));
    }

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public Review review(Authentication auth, @PathVariable UUID bookingId, @RequestBody SubmitReviewRequest body) {
        return reviews.create(new CreateReviewRequest(
                UUID.fromString(auth.getName()), bookingId, body.rating(), body.comment()));
    }
}
