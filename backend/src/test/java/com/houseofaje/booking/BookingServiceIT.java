package com.houseofaje.booking;

import java.math.BigDecimal;
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

import static com.houseofaje.booking.BookingStatus.CANCELLED;
import static com.houseofaje.booking.BookingStatus.COMPLETED;
import static com.houseofaje.booking.BookingStatus.CONFIRMED;
import static com.houseofaje.booking.BookingStatus.EXPIRED;
import static com.houseofaje.booking.BookingStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class BookingServiceIT {

    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookings;

    @Autowired
    ReconciliationService reconciliationService;

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
    void createPlacesPendingPaymentWithFortyPercentDeposit() {
        BookingResult result = createAt("2026-01-12T10:00", "idem-1");

        Booking booking = result.booking();
        assertThat(booking.status()).isEqualTo(PENDING_PAYMENT);
        assertThat(booking.price()).isEqualByComparingTo("10000.00");
        assertThat(booking.depositAmount()).isEqualByComparingTo("4000.00");
        assertThat(booking.reference()).hasSize(32);
        assertThat(booking.endAt()).isEqualTo(at("2026-01-12T11:00"));
        assertThat(result.depositInstructions().accountNumber()).isNotBlank();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE idempotency_key = ? AND type = 'DEPOSIT' AND amount = 4000.00 AND status = 'PENDING'",
                Integer.class, "idem-1")).isEqualTo(1);
    }

    @Test
    void createIsIdempotentOnTheSameKey() {
        BookingResult first = createAt("2026-01-12T10:00", "idem-2");
        BookingResult second = createAt("2026-01-12T10:00", "idem-2");

        assertThat(second.booking().id()).isEqualTo(first.booking().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE idempotency_key = 'idem-2'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsOverlappingSlot() {
        createAt("2026-01-12T10:00", "idem-3");

        assertThatThrownBy(() -> createAt("2026-01-12T10:30", "idem-4"))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void allowsBackToBackBooking() {
        createAt("2026-01-12T09:00", "idem-5");

        BookingResult second = createAt("2026-01-12T10:00", "idem-6");

        assertThat(second.booking().startAt()).isEqualTo(at("2026-01-12T10:00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(2);
    }

    @Test
    void confirmThenComplete() {
        Booking created = createAt("2026-01-12T10:00", "idem-7").booking();

        Booking confirmed = bookingService.confirm(created.id());
        assertThat(confirmed.status()).isEqualTo(CONFIRMED);

        Booking completed = bookingService.complete(created.id());
        assertThat(completed.status()).isEqualTo(COMPLETED);
    }

    @Test
    void completeRecordsSixtyPercentBalanceAsPaidInPerson() {
        Booking created = createAt("2026-01-12T10:00", "idem-balance").booking();
        bookingService.confirm(created.id());

        bookingService.complete(created.id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE booking_id = ? AND type = 'BALANCE' AND amount = 6000.00 AND status = 'PAID'",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void completeFromPendingPaymentIsRejected() {
        Booking created = createAt("2026-01-12T10:00", "idem-8").booking();

        assertThatThrownBy(() -> bookingService.complete(created.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelReleasesTheSlot() {
        Booking created = createAt("2026-01-12T10:00", "idem-9").booking();

        assertThat(bookingService.cancel(created.id()).status()).isEqualTo(CANCELLED);

        // The slot is free again: the same time can be rebooked.
        BookingResult rebooked = createAt("2026-01-12T10:00", "idem-10");
        assertThat(rebooked.booking().status()).isEqualTo(PENDING_PAYMENT);
    }

    @Test
    void cancelConfirmedBookingRefundsThirtyPercentAndRetainsFee() {
        Booking created = createAt("2026-01-12T10:00", "idem-refund").booking();
        // Simulate a settled deposit with a known transaction reference.
        jdbc.update("UPDATE payments SET status = 'PAID', provider_transaction_reference = 'txn_x' "
                + "WHERE booking_id = ? AND type = 'DEPOSIT'", created.id());
        bookingService.confirm(created.id());

        Booking cancelled = bookingService.cancel(created.id());
        assertThat(cancelled.status()).isEqualTo(CANCELLED);

        // 30% of 10000.00 refunded; the other 10% of the deposit is retained.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE booking_id = ? AND type = 'REFUND' AND amount = 3000.00 AND status = 'PAID'",
                Integer.class, created.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity = 'booking' AND entity_id = ? AND action = 'CANCELLED'",
                Integer.class, created.id().toString())).isEqualTo(1);
    }

    @Test
    void expireFromPendingPayment() {
        Booking created = createAt("2026-01-12T10:00", "idem-11").booking();

        assertThat(bookingService.expire(created.id()).status()).isEqualTo(EXPIRED);
    }

    @Test
    void adminListJoinsCustomerServiceAndStaffNames() {
        Booking created = createAt("2026-01-12T10:00", "idem-admin-list").booking();

        var rows = bookings.findForAdmin(null, 50);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(created.id());
        assertThat(rows.get(0).customerName()).isEqualTo("Test User");
        assertThat(rows.get(0).serviceName()).isEqualTo("braiding");
        assertThat(rows.get(0).staffName()).isEqualTo("Ada");
    }

    @Test
    void reconciliationSweepExpiresAbandonedPendingPayment() {
        Booking created = createAt("2026-01-12T10:00", "idem-sweep").booking();
        // Backdate past the 15-minute payment window.
        jdbc.update("UPDATE bookings SET created_at = now() - interval '20 minutes' WHERE id = ?", created.id());

        reconciliationService.expireAbandonedBookings();

        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, created.id()))
                .isEqualTo(EXPIRED.name());
    }

    @Test
    void confirmEmitsConfirmationNotification() {
        Booking created = createAt("2026-01-12T10:00", "idem-notif-confirm").booking();

        bookingService.confirm(created.id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ? AND type = 'CONFIRMATION' AND channel = 'EMAIL' AND recipient = 'test@example.com'",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void completeEmitsReviewRequestNotification() {
        Booking created = createAt("2026-01-12T10:00", "idem-notif-review").booking();
        bookingService.confirm(created.id());

        bookingService.complete(created.id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ? AND type = 'REVIEW_REQUEST'",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void cancelEmitsCancellationNotification() {
        Booking created = createAt("2026-01-12T10:00", "idem-notif-cancel").booking();

        bookingService.cancel(created.id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ? AND type = 'CANCELLATION'",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void notificationFallsBackToSmsWithoutEmail() {
        UUID noEmailCustomer = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, first_name, last_name, phone) VALUES (?, 'No', 'Email', '08123456789')",
                noEmailCustomer);

        BookingResult result = bookingService.create(
                new CreateBookingRequest(noEmailCustomer, service, staff, at("2026-01-12T10:00"), false), "idem-notif-sms");
        bookingService.confirm(result.booking().id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ? AND type = 'CONFIRMATION' AND channel = 'SMS' AND recipient = '08123456789'",
                Integer.class, result.booking().id())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private BookingResult createAt(String start, String idempotencyKey) {
        return bookingService.create(
                new CreateBookingRequest(customer, service, staff, at(start), false),
                idempotencyKey);
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

    private static java.time.Instant at(String s) {
        return LocalDateTime.parse(s).atZone(ZONE).toInstant();
    }
}
