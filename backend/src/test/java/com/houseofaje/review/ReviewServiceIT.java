package com.houseofaje.review;

import com.houseofaje.booking.BookingResult;
import com.houseofaje.booking.BookingService;
import com.houseofaje.booking.CreateBookingRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class ReviewServiceIT {

    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookingService bookingService;

    @Autowired
    ReviewService reviewService;

    private UUID customer;
    private UUID service;
    private UUID staff;

    @BeforeEach
    void seed() {
        customer = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, first_name, last_name, email, phone) VALUES (?, 'Test', 'User', 'test@example.com', '08000000000')",
                customer);
        service = seedService("braiding", 60, 0, "10000.00");
        staff = seedStaff("Ada");
        link(staff, service);
        addWorkingHours(staff, 1, "09:00", "17:00");
    }

    @Test
    void createsReviewForCompletedBookingAndListsItPublished() {
        UUID bookingId = completedBooking("2026-01-12T10:00", "idem-review-1");

        Review created = reviewService.create(new CreateReviewRequest(customer, bookingId, 4, "Great braids"));

        assertThat(created.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(reviewService.publishedByService(service, 10))
                .extracting(PublishedReview::rating)
                .containsExactly(4);
    }

    @Test
    void rejectsReviewForNonCompletedBooking() {
        UUID bookingId = bookingService.create(
                new CreateBookingRequest(customer, service, staff, at("2026-01-12T10:00"), false), "idem-review-2")
                .booking().id();

        assertThatThrownBy(() -> reviewService.create(new CreateReviewRequest(customer, bookingId, 5, "early")))
                .isInstanceOf(BookingNotCompletedException.class);
    }

    @Test
    void rejectsReviewByAnotherCustomer() {
        UUID bookingId = completedBooking("2026-01-12T10:00", "idem-review-3");

        assertThatThrownBy(() -> reviewService.create(new CreateReviewRequest(UUID.randomUUID(), bookingId, 5, "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateReviewForTheSameBooking() {
        UUID bookingId = completedBooking("2026-01-12T10:00", "idem-review-4");
        reviewService.create(new CreateReviewRequest(customer, bookingId, 5, "once"));

        assertThatThrownBy(() -> reviewService.create(new CreateReviewRequest(customer, bookingId, 5, "again")))
                .isInstanceOf(ReviewAlreadyExistsException.class);
    }

    @Test
    void hidingRemovesFromPublicListingUntilRepublished() {
        UUID bookingId = completedBooking("2026-01-12T10:00", "idem-review-5");
        Review created = reviewService.create(new CreateReviewRequest(customer, bookingId, 5, "moderate me"));

        reviewService.setStatus(created.id(), ReviewStatus.HIDDEN);
        assertThat(reviewService.publishedByService(service, 10)).isEmpty();

        reviewService.setStatus(created.id(), ReviewStatus.PUBLISHED);
        assertThat(reviewService.publishedByService(service, 10)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Creates, confirms and completes a booking, returning its id. */
    private UUID completedBooking(String start, String idempotencyKey) {
        BookingResult created = bookingService.create(
                new CreateBookingRequest(customer, service, staff, at(start), false), idempotencyKey);
        bookingService.confirm(created.booking().id());
        return bookingService.complete(created.booking().id()).id();
    }

    private UUID seedService(String slug, int durationMinutes, int bufferMinutes, String price) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO services (id, name, slug, duration_minutes, price, buffer_minutes) VALUES (?, ?, ?, ?, ?, ?)",
                id, slug, slug, durationMinutes, new BigDecimal(price), bufferMinutes);
        return id;
    }

    private UUID seedStaff(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private void link(UUID staffId, UUID serviceId) {
        jdbc.update("INSERT INTO staff_services (staff_id, service_id) VALUES (?, ?)", staffId, serviceId);
    }

    private void addWorkingHours(UUID staffId, int dayOfWeek, String start, String end) {
        jdbc.update("INSERT INTO staff_working_hours (id, staff_id, day_of_week, start_time, end_time) VALUES (gen_random_uuid(), ?, ?, ?, ?)",
                staffId, dayOfWeek, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Instant at(String s) {
        return LocalDateTime.parse(s).atZone(ZONE).toInstant();
    }
}
