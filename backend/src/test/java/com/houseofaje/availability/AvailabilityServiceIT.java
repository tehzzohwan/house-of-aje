package com.houseofaje.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

@SpringBootTest
@Testcontainers
@Transactional
class AvailabilityServiceIT {

    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");
    private static final LocalDate MON = LocalDate.of(2026, 1, 12); // Monday

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AvailabilityService availabilityService;

    private UUID customer;

    @BeforeEach
    void seedCustomer() {
        customer = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, first_name, last_name, phone) VALUES (?, 'Test', 'User', '08000000000')", customer);
    }

    @Test
    void returnsSlotsIntersectingBusinessAndStaffHours() {
        UUID service = seedService("braiding", 60, 0);
        UUID staff = seedStaff("Ada");
        link(staff, service);
        addWorkingHours(staff, 1, "09:00", "17:00");

        var starts = localStarts(availabilityService.availableSlots(service, staff, MON));

        assertThat(starts).contains(at("2026-01-12T09:00"));
        assertThat(starts).contains(at("2026-01-12T16:00")); // last 60m slot before 17:00 close
        assertThat(starts).doesNotContain(at("2026-01-12T16:15"));
        assertThat(starts).doesNotContain(at("2026-01-12T08:45")); // before 09:00 open
        assertThat(starts).hasSize(29);
    }

    @Test
    void subtractsExistingBookingsAndTheirBuffer() {
        UUID bookedService = seedService("braiding", 60, 15);
        UUID queriedService = seedService("weave", 60, 0);
        UUID staff = seedStaff("Ada");
        link(staff, bookedService);
        link(staff, queriedService);
        addWorkingHours(staff, 1, "09:00", "17:00");
        addBooking(staff, bookedService, at("2026-01-12T10:00"), at("2026-01-12T11:00"));

        var starts = localStarts(availabilityService.availableSlots(queriedService, staff, MON));

        assertThat(starts).contains(at("2026-01-12T09:00"));
        assertThat(starts).doesNotContain(at("2026-01-12T09:15")); // overlaps occupied [10:00, 11:15)
        assertThat(starts).doesNotContain(at("2026-01-12T11:00")); // inside the booking's 15m buffer
        assertThat(starts).contains(at("2026-01-12T11:15"));       // first slot after the buffer
    }

    @Test
    void newBookingTrailingBufferBlocksCollidingSlot() {
        UUID queriedService = seedService("braiding", 60, 15);
        UUID existingService = seedService("weave", 60, 0);
        UUID staff = seedStaff("Ada");
        link(staff, queriedService);
        link(staff, existingService);
        addWorkingHours(staff, 1, "09:00", "17:00");
        addBooking(staff, existingService, at("2026-01-12T11:00"), at("2026-01-12T12:00"));

        var starts = localStarts(availabilityService.availableSlots(queriedService, staff, MON));

        assertThat(starts).contains(at("2026-01-12T09:45"));      // buffer ends exactly at the 11:00 booking
        assertThat(starts).doesNotContain(at("2026-01-12T10:00")); // 15m trailing buffer would eat into 11:00
        assertThat(starts).contains(at("2026-01-12T12:00"));       // clear of the booking
    }

    @Test
    void subtractsTimeOff() {
        UUID service = seedService("braiding", 60, 0);
        UUID staff = seedStaff("Ada");
        link(staff, service);
        addWorkingHours(staff, 1, "09:00", "17:00");
        addTimeOff(staff, at("2026-01-12T09:00"), at("2026-01-12T10:00"));

        var starts = localStarts(availabilityService.availableSlots(service, staff, MON));

        assertThat(starts).doesNotContain(at("2026-01-12T09:00"));
        assertThat(starts).contains(at("2026-01-12T10:00"));
        assertThat(starts).contains(at("2026-01-12T16:00"));
    }

    @Test
    void returnsEmptyWhenStaffDoesNotProvideService() {
        UUID service = seedService("braiding", 60, 0);
        UUID staff = seedStaff("Ada");
        // no staff_services link
        addWorkingHours(staff, 1, "09:00", "17:00");

        assertThat(availabilityService.availableSlots(service, staff, MON)).isEmpty();
    }

    @Test
    void optionalStaffReturnsSlotsAcrossQualifiedStaff() {
        UUID service = seedService("braiding", 60, 0);
        UUID ada = seedStaff("Ada");
        UUID bisi = seedStaff("Bisi");
        link(ada, service);
        link(bisi, service);
        addWorkingHours(ada, 1, "09:00", "17:00");
        // Bisi has no working hours on Monday -> contributes nothing

        var slots = availabilityService.availableSlots(service, null, MON);

        assertThat(slots).hasSize(29);
        assertThat(slots).allMatch(s -> s.staffId().equals(ada));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private UUID seedService(String slug, int durationMinutes, int bufferMinutes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO services (id, name, slug, duration_minutes, price, buffer_minutes) VALUES (?, ?, ?, ?, ?, ?)",
                id, slug, slug, durationMinutes, new BigDecimal("10000.00"), bufferMinutes);
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

    private void addTimeOff(UUID staffId, LocalDateTime start, LocalDateTime end) {
        jdbc.update("INSERT INTO staff_time_off (id, staff_id, starts_at, ends_at) VALUES (gen_random_uuid(), ?, ?, ?)",
                staffId, start.atZone(ZONE).toOffsetDateTime(), end.atZone(ZONE).toOffsetDateTime());
    }

    private void addBooking(UUID staffId, UUID serviceId, LocalDateTime start, LocalDateTime end) {
        jdbc.update("""
                INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
                VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', 10000.00, 4000.00)
                """,
                UUID.randomUUID().toString().replace("-", ""), customer, serviceId, staffId,
                start.atZone(ZONE).toOffsetDateTime(), end.atZone(ZONE).toOffsetDateTime());
    }

    private static LocalDateTime at(String s) {
        return LocalDateTime.parse(s);
    }

    private Set<LocalDateTime> localStarts(List<AvailabilitySlot> slots) {
        return slots.stream()
                .map(s -> s.start().atZone(ZONE).toLocalDateTime())
                .collect(Collectors.toSet());
    }
}
