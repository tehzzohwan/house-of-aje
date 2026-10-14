package com.houseofaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database-level no-double-booking guarantee ({@code bookings_no_double_booking})
 * is enforced by Postgres itself, independent of any application code. These tests
 * exercise the exclusion constraint directly against a real PostgreSQL via Testcontainers.
 */
@SpringBootTest
@Testcontainers
@Transactional
class BookingExclusionConstraintIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    private static final UUID SERVICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BISI = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void seed() {
        jdbc.update("""
                INSERT INTO services (id, name, slug, duration_minutes, price, buffer_minutes)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, SERVICE, "Braiding", "braiding", 60, new BigDecimal("10000.00"), 15);
        jdbc.update("INSERT INTO staff (id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", ADA, "Ada");
        jdbc.update("INSERT INTO staff (id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", BISI, "Bisi");
        jdbc.update("""
                INSERT INTO customers (id, first_name, last_name, phone)
                VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, CUSTOMER, "Test", "User", "08000000000");
    }

    private void insertBooking(String reference, UUID staffId, String startAt, String endAt) {
        jdbc.update("""
                INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
                VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', 10000.00, 4000.00)
                """,
                reference, CUSTOMER, SERVICE, staffId,
                OffsetDateTime.parse(startAt), OffsetDateTime.parse(endAt));
    }

    @Test
    void rejectsOverlappingBookingForSameStaff() {
        insertBooking("IT-A", ADA, "2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");

        assertThatThrownBy(() ->
                insertBooking("IT-B", ADA, "2026-01-12T10:30:00Z", "2026-01-12T11:30:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsBackToBackAndDifferentStaff() {
        insertBooking("IT-C", ADA, "2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");

        // back-to-back (adjacent, not overlapping)
        assertThatCode(() ->
                insertBooking("IT-D", ADA, "2026-01-12T11:00:00Z", "2026-01-12T12:00:00Z"))
                .doesNotThrowAnyException();

        // same slot, different staff
        assertThatCode(() ->
                insertBooking("IT-E", BISI, "2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelledBookingReleasesTheSlot() {
        insertBooking("IT-F", ADA, "2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");
        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE reference = ?", "IT-F");

        assertThatCode(() ->
                insertBooking("IT-G", ADA, "2026-01-12T10:30:00Z", "2026-01-12T11:30:00Z"))
                .doesNotThrowAnyException();
    }
}
