package com.houseofaje.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
class ReportingServiceIT {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReportingService reporting;

    @Test
    void profitIsGrossMinusRefunds() {
        UUID braiding = service("Braiding", 10000);
        UUID nails = service("Nails", 5000);
        UUID ada = staff("Ada");
        UUID customer = customer("Ada", "Lovelace", "ada@example.com");

        // Completed: 40% deposit + 60% balance = full price collected.
        UUID b1 = booking(customer, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T09:00:00Z"));
        payment(b1, "4000.00", "DEPOSIT", "PAID", Instant.parse("2026-01-02T00:00:00Z"));
        payment(b1, "6000.00", "BALANCE", "PAID", Instant.parse("2026-01-12T10:00:00Z"));

        // Cancelled after deposit: 30% of 5000 refunded, 10% retained.
        UUID b2 = booking(customer, nails, ada, "CANCELLED", "5000.00", Instant.parse("2026-01-13T09:00:00Z"));
        payment(b2, "2000.00", "DEPOSIT", "PAID", Instant.parse("2026-01-04T00:00:00Z"));
        payment(b2, "1500.00", "REFUND", "PAID", Instant.parse("2026-01-05T00:00:00Z"));

        ProfitReport report = reporting.profit(null, null);

        assertThat(report.grossRevenue()).isEqualByComparingTo("12000.00");
        assertThat(report.refunds()).isEqualByComparingTo("1500.00");
        assertThat(report.netProfit()).isEqualByComparingTo("10500.00");
    }

    @Test
    void bestSellingRanksByCompletedRevenue() {
        UUID braiding = service("Braiding", 10000);
        UUID nails = service("Nails", 5000);
        UUID ada = staff("Ada");
        UUID customer = customer("Ada", "Lovelace", "ada@example.com");

        booking(customer, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T09:00:00Z"));
        booking(customer, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T10:30:00Z"));
        booking(customer, nails, ada, "COMPLETED", "5000.00", Instant.parse("2026-01-12T12:00:00Z"));

        List<BestSeller> report = reporting.bestSelling(null, null);

        assertThat(report).hasSize(2);
        assertThat(report.get(0).serviceName()).isEqualTo("Braiding");
        assertThat(report.get(0).completed()).isEqualTo(2);
        assertThat(report.get(0).revenue()).isEqualByComparingTo("20000.00");
        assertThat(report.get(1).serviceName()).isEqualTo("Nails");
        assertThat(report.get(1).revenue()).isEqualByComparingTo("5000.00");
    }

    @Test
    void staffPerformanceAndLoyaltyAggregate() {
        UUID braiding = service("Braiding", 10000);
        UUID ada = staff("Ada");
        UUID loyal = customer("Repeat", "Client", "repeat@example.com");
        UUID once = customer("One", "Timer", "once@example.com");

        booking(loyal, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T09:00:00Z"));
        booking(loyal, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T10:30:00Z"));
        booking(once, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T12:00:00Z"));

        List<StaffPerformance> staff = reporting.staffPerformance(null, null);
        assertThat(staff).hasSize(1);
        assertThat(staff.get(0).staffName()).isEqualTo("Ada");
        assertThat(staff.get(0).bookings()).isEqualTo(3);
        assertThat(staff.get(0).revenue()).isEqualByComparingTo("30000.00");

        List<LoyalCustomer> loyalty = reporting.loyalty(null, null, 10);
        assertThat(loyalty).hasSize(2);
        assertThat(loyalty.get(0).name()).isEqualTo("Repeat Client");
        assertThat(loyalty.get(0).bookings()).isEqualTo(2);
        assertThat(loyalty.get(1).name()).isEqualTo("One Timer");
    }

    @Test
    void bookingsCsvExportsRows() {
        UUID braiding = service("Braiding", 10000);
        UUID ada = staff("Ada");
        UUID customer = customer("Ada", "Lovelace", "ada@example.com");
        UUID b1 = booking(customer, braiding, ada, "COMPLETED", "10000.00", Instant.parse("2026-01-12T09:00:00Z"));

        String reference = jdbc.queryForObject("SELECT reference FROM bookings WHERE id = ?", String.class, b1);

        String csv = reporting.bookingsCsv(null, null);

        assertThat(csv).startsWith("reference,customer,service,staff,start_at,end_at,status,price,deposit_amount\n");
        assertThat(csv).contains(reference);
        assertThat(csv).contains("Ada Lovelace");
        assertThat(csv).contains("Braiding");
        assertThat(csv).contains(",COMPLETED,10000.00,4000.00");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private UUID service(String name, int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO services (id, name, slug, duration_minutes, price) VALUES (?, ?, ?, 60, ?)",
                id, name, name.toLowerCase() + "-" + id.toString().substring(0, 8), new BigDecimal(price));
        return id;
    }

    private UUID staff(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID customer(String first, String last, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, first_name, last_name, email, phone) VALUES (?, ?, ?, ?, '08000000000')",
                id, first, last, email);
        return id;
    }

    private UUID booking(UUID customer, UUID service, UUID staff, String status, String price, Instant startAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bookings (id, reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, UUID.randomUUID().toString().replace("-", ""), customer, service, staff,
                OffsetDateTime.ofInstant(startAt, UTC),
                OffsetDateTime.ofInstant(startAt.plusSeconds(3600), UTC),
                status, new BigDecimal(price), new BigDecimal(price).multiply(new BigDecimal("0.40")));
        return id;
    }

    private void payment(UUID bookingId, String amount, String type, String status, Instant paidAt) {
        jdbc.update("""
                INSERT INTO payments (id, booking_id, amount, type, status, provider, idempotency_key, currency, paid_at)
                VALUES (?, ?, ?, ?, ?, 'STUB', ?, 'NGN', ?)
                """,
                UUID.randomUUID(), bookingId, new BigDecimal(amount), type, status, UUID.randomUUID().toString(),
                OffsetDateTime.ofInstant(paidAt, UTC));
    }
}
