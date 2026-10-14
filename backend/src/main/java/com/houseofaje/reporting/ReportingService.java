package com.houseofaje.reporting;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only reporting queries. All booking-based reports are filtered by
 * {@code created_at} (when the booking was placed); profit is filtered by
 * {@code paid_at} (when money actually moved). A null bound means "all time".
 */
@Service
public class ReportingService {

    private static final Instant ALL_TIME_FROM = Instant.EPOCH;
    private static final Instant ALL_TIME_TO = Instant.parse("9999-12-31T23:59:59Z");

    private final JdbcTemplate jdbc;
    private final ZoneId businessZone;

    public ReportingService(JdbcTemplate jdbc, @Value("${hoa.business-zone:Africa/Lagos}") String businessZone) {
        this.jdbc = jdbc;
        this.businessZone = ZoneId.of(businessZone);
    }

    public ProfitReport profit(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.queryForObject("""
                SELECT
                  COALESCE(SUM(amount) FILTER (WHERE type IN ('DEPOSIT','BALANCE') AND status = 'PAID'), 0) AS gross,
                  COALESCE(SUM(amount) FILTER (WHERE type = 'REFUND' AND status IN ('PAID','PENDING')), 0) AS refunds
                FROM payments
                WHERE paid_at >= ? AND paid_at < ? AND deleted_at IS NULL
                """,
                (rs, n) -> {
                    BigDecimal gross = rs.getBigDecimal("gross");
                    BigDecimal refunds = rs.getBigDecimal("refunds");
                    return new ProfitReport(gross, refunds, gross.subtract(refunds));
                },
                w.from(), w.to());
    }

    public List<ServiceBookings> bookingsPerService(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT s.name, COUNT(b.id) AS bookings
                FROM bookings b
                JOIN services s ON s.id = b.service_id
                WHERE b.created_at >= ? AND b.created_at < ? AND b.deleted_at IS NULL
                GROUP BY s.id, s.name
                ORDER BY bookings DESC, s.name
                """,
                (rs, n) -> new ServiceBookings(rs.getString("name"), rs.getLong("bookings")),
                w.from(), w.to());
    }

    public List<BestSeller> bestSelling(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT s.name, COUNT(b.id) AS completed, COALESCE(SUM(b.price), 0) AS revenue
                FROM bookings b
                JOIN services s ON s.id = b.service_id
                WHERE b.status = 'COMPLETED' AND b.created_at >= ? AND b.created_at < ? AND b.deleted_at IS NULL
                GROUP BY s.id, s.name
                ORDER BY revenue DESC, s.name
                """,
                (rs, n) -> new BestSeller(rs.getString("name"), rs.getLong("completed"), rs.getBigDecimal("revenue")),
                w.from(), w.to());
    }

    public List<LoyalCustomer> loyalty(LocalDate from, LocalDate to, int limit) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT c.id, c.first_name, c.last_name, c.email, COUNT(b.id) AS bookings
                FROM bookings b
                JOIN customers c ON c.id = b.customer_id
                WHERE b.status = 'COMPLETED' AND b.created_at >= ? AND b.created_at < ? AND b.deleted_at IS NULL
                GROUP BY c.id, c.first_name, c.last_name, c.email
                ORDER BY bookings DESC, c.last_name
                LIMIT ?
                """,
                (rs, n) -> new LoyalCustomer(
                        rs.getObject("id", UUID.class),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getLong("bookings")),
                w.from(), w.to(), limit);
    }

    public List<StaffPerformance> staffPerformance(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT st.name,
                       COUNT(DISTINCT b.id) AS bookings,
                       COUNT(DISTINCT b.id) FILTER (WHERE b.status = 'COMPLETED') AS completed,
                       COUNT(DISTINCT b.id) FILTER (WHERE b.status = 'CANCELLED') AS cancelled,
                       COALESCE(SUM(p.amount), 0) AS revenue
                FROM bookings b
                JOIN staff st ON st.id = b.staff_id
                LEFT JOIN payments p ON p.booking_id = b.id
                    AND p.deleted_at IS NULL
                    AND p.type IN ('DEPOSIT','BALANCE')
                    AND p.status = 'PAID'
                    AND p.paid_at >= ? AND p.paid_at < ?
                WHERE b.created_at >= ? AND b.created_at < ? AND b.deleted_at IS NULL
                GROUP BY st.id, st.name
                ORDER BY revenue DESC, st.name
                """,
                (rs, n) -> new StaffPerformance(
                        rs.getString("name"),
                        rs.getLong("bookings"),
                        rs.getLong("completed"),
                        rs.getLong("cancelled"),
                        rs.getBigDecimal("revenue")),
                w.from(), w.to(), w.from(), w.to());
    }

    public String bookingsCsv(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT b.reference, c.first_name || ' ' || c.last_name AS customer,
                       s.name AS service, st.name AS staff,
                       b.start_at, b.end_at, b.status, b.price, b.deposit_amount
                FROM bookings b
                JOIN customers c ON c.id = b.customer_id
                JOIN services s ON s.id = b.service_id
                JOIN staff st ON st.id = b.staff_id
                WHERE b.created_at >= ? AND b.created_at < ? AND b.deleted_at IS NULL
                ORDER BY b.start_at
                """,
                rs -> {
                    StringBuilder csv = new StringBuilder(
                            "reference,customer,service,staff,start_at,end_at,status,price,deposit_amount\n");
                    while (rs.next()) {
                        csv.append(cell(rs.getString("reference"))).append(',')
                                .append(cell(rs.getString("customer"))).append(',')
                                .append(cell(rs.getString("service"))).append(',')
                                .append(cell(rs.getString("staff"))).append(',')
                                .append(cell(offsetDateTime(rs, "start_at"))).append(',')
                                .append(cell(offsetDateTime(rs, "end_at"))).append(',')
                                .append(cell(rs.getString("status"))).append(',')
                                .append(rs.getBigDecimal("price")).append(',')
                                .append(rs.getBigDecimal("deposit_amount")).append('\n');
                    }
                    return csv.toString();
                },
                w.from(), w.to());
    }

    /** Money ledger: every payment row joined with booking/customer/service, oldest first. */
    public String paymentsCsv(LocalDate from, LocalDate to) {
        Window w = window(from, to);
        return jdbc.query("""
                SELECT p.created_at, p.paid_at, p.amount, p.type, p.status, p.currency, p.provider,
                       p.provider_reference, p.provider_transaction_reference,
                       b.reference AS booking_reference,
                       c.first_name || ' ' || c.last_name AS customer,
                       s.name AS service
                FROM payments p
                LEFT JOIN bookings b ON b.id = p.booking_id
                LEFT JOIN customers c ON c.id = b.customer_id
                LEFT JOIN services s ON s.id = b.service_id
                WHERE p.created_at >= ? AND p.created_at < ? AND p.deleted_at IS NULL
                ORDER BY p.created_at, p.id
                """,
                rs -> {
                    StringBuilder csv = new StringBuilder(
                            "created_at,paid_at,booking_reference,customer,service,type,status,currency,amount,provider,provider_reference,provider_transaction_reference\n");
                    while (rs.next()) {
                        csv.append(cell(offsetDateTime(rs, "created_at"))).append(',')
                                .append(cell(offsetDateTime(rs, "paid_at"))).append(',')
                                .append(cell(rs.getString("booking_reference"))).append(',')
                                .append(cell(rs.getString("customer"))).append(',')
                                .append(cell(rs.getString("service"))).append(',')
                                .append(cell(rs.getString("type"))).append(',')
                                .append(cell(rs.getString("status"))).append(',')
                                .append(cell(rs.getString("currency"))).append(',')
                                .append(rs.getBigDecimal("amount")).append(',')
                                .append(cell(rs.getString("provider"))).append(',')
                                .append(cell(rs.getString("provider_reference"))).append(',')
                                .append(cell(rs.getString("provider_transaction_reference"))).append('\n');
                    }
                    return csv.toString();
                },
                w.from(), w.to());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Window window(LocalDate from, LocalDate to) {
        Instant fromI = from == null ? ALL_TIME_FROM : from.atStartOfDay(businessZone).toInstant();
        Instant toI = to == null ? ALL_TIME_TO : to.plusDays(1).atStartOfDay(businessZone).toInstant();
        return new Window(OffsetDateTime.ofInstant(fromI, ZoneOffset.UTC), OffsetDateTime.ofInstant(toI, ZoneOffset.UTC));
    }

    private static String offsetDateTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? "" : value.toInstant().toString();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record Window(OffsetDateTime from, OffsetDateTime to) {}
}
