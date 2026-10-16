package com.houseofaje.booking;

import com.houseofaje.payment.PaymentState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Booking> MAPPER = (rs, n) -> new Booking(
            rs.getObject("id", UUID.class),
            rs.getString("reference"),
            rs.getObject("customer_id", UUID.class),
            rs.getObject("service_id", UUID.class),
            rs.getObject("staff_id", UUID.class),
            rs.getObject("start_at", OffsetDateTime.class).toInstant(),
            rs.getObject("end_at", OffsetDateTime.class).toInstant(),
            BookingStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("deposit_amount"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public void insert(Booking b) {
        jdbc.update("""
                INSERT INTO bookings (id, reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                b.id(), b.reference(), b.customerId(), b.serviceId(), b.staffId(),
                OffsetDateTime.ofInstant(b.startAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(b.endAt(), ZoneOffset.UTC),
                b.status().name(), b.price(), b.depositAmount());
    }

    public Optional<Booking> findById(UUID id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ? AND deleted_at IS NULL", MAPPER, id)
                .stream().findFirst();
    }

    /** Bookings still awaiting deposit whose payment window has elapsed. */
    public List<Booking> findPendingPaymentBefore(Instant cutoff) {
        return jdbc.query(
                "SELECT * FROM bookings WHERE status = 'PENDING_PAYMENT' AND created_at <= ? AND deleted_at IS NULL "
                        + "ORDER BY created_at",
                MAPPER, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }

    /** Confirmed bookings starting in the half-open window {@code (from, to]}. */
    public List<Booking> findConfirmedStartingBetween(Instant from, Instant to) {
        return jdbc.query(
                "SELECT * FROM bookings WHERE status = 'CONFIRMED' AND start_at > ? AND start_at <= ? AND deleted_at IS NULL "
                        + "ORDER BY start_at",
                MAPPER, OffsetDateTime.ofInstant(from, ZoneOffset.UTC), OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
    }

    /** Confirmed bookings whose scheduled end time is on or before {@code cutoff}. */
    public List<Booking> findConfirmedEndedBefore(Instant cutoff) {
        return jdbc.query(
                "SELECT * FROM bookings WHERE status = 'CONFIRMED' AND end_at <= ? AND deleted_at IS NULL "
                        + "ORDER BY end_at",
                MAPPER, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }

    /** Bookings joined with names, newest first, optionally filtered by status. */
    public AdminBookingPage findForAdminPage(BookingStatus status, int offset, int size) {
        String countBase = "SELECT count(*) FROM bookings b WHERE b.deleted_at IS NULL";
        long total = status == null
                ? jdbc.queryForObject(countBase, Long.class)
                : jdbc.queryForObject(countBase + " AND b.status = ?", Long.class, status.name());

        String base = """
                SELECT b.id, b.reference,
                       c.first_name || ' ' || c.last_name AS customer_name,
                       s.name AS service_name, st.name AS staff_name,
                       b.start_at, b.end_at, b.status, b.price, b.deposit_amount,
                       d.status AS deposit_status, bal.status AS balance_status
                FROM bookings b
                JOIN customers c ON c.id = b.customer_id
                JOIN services s ON s.id = b.service_id
                JOIN staff st ON st.id = b.staff_id
                LEFT JOIN payments d ON d.booking_id = b.id AND d.type = 'DEPOSIT' AND d.deleted_at IS NULL
                LEFT JOIN payments bal ON bal.booking_id = b.id AND bal.type = 'BALANCE' AND bal.deleted_at IS NULL
                WHERE b.deleted_at IS NULL
                """;
        RowMapper<AdminBooking> mapper = (rs, n) -> {
            BigDecimal price = rs.getBigDecimal("price");
            BigDecimal deposit = rs.getBigDecimal("deposit_amount");
            return new AdminBooking(
                    rs.getObject("id", UUID.class),
                    rs.getString("reference"),
                    rs.getString("customer_name"),
                    rs.getString("service_name"),
                    rs.getString("staff_name"),
                    rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("end_at", OffsetDateTime.class).toInstant(),
                    BookingStatus.valueOf(rs.getString("status")),
                    price,
                    deposit,
                    paymentState(rs.getString("deposit_status"), rs.getString("balance_status"), price, deposit));
        };
        List<AdminBooking> items = status == null
                ? jdbc.query(base + " ORDER BY b.start_at DESC LIMIT ? OFFSET ?", mapper, size, offset)
                : jdbc.query(base + " AND b.status = ? ORDER BY b.start_at DESC LIMIT ? OFFSET ?",
                        mapper, status.name(), size, offset);
        return new AdminBookingPage(items, total);
    }

    private static final RowMapper<BookingSummary> SUMMARY_MAPPER = (rs, n) -> {
        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal deposit = rs.getBigDecimal("deposit_amount");
        return new BookingSummary(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("service_name"),
                rs.getString("staff_name"),
                rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                BookingStatus.valueOf(rs.getString("status")),
                price,
                deposit,
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("reviewed"),
                paymentState(rs.getString("deposit_status"), rs.getString("balance_status"), price, deposit));
    };

    private static final String SUMMARY_SQL = """
            SELECT b.id, b.reference, s.name AS service_name, st.name AS staff_name,
                   b.start_at, b.status, b.price, b.deposit_amount, b.created_at,
                   (r.id IS NOT NULL) AS reviewed,
                   d.status AS deposit_status, bal.status AS balance_status
            FROM bookings b
            JOIN services s ON s.id = b.service_id
            JOIN staff st ON st.id = b.staff_id
            LEFT JOIN reviews r ON r.booking_id = b.id
            LEFT JOIN payments d ON d.booking_id = b.id AND d.type = 'DEPOSIT' AND d.deleted_at IS NULL
            LEFT JOIN payments bal ON bal.booking_id = b.id AND bal.type = 'BALANCE' AND bal.deleted_at IS NULL
            WHERE b.deleted_at IS NULL
            """;

    /** A customer's bookings, newest first. */
    public List<BookingSummary> findByCustomer(UUID customerId) {
        return jdbc.query(SUMMARY_SQL + " AND b.customer_id = ? ORDER BY b.start_at DESC",
                SUMMARY_MAPPER, customerId);
    }

    /** A staff member's bookings, newest first. */
    public List<BookingSummary> findByStaff(UUID staffId) {
        return jdbc.query(SUMMARY_SQL + " AND b.staff_id = ? ORDER BY b.start_at DESC",
                SUMMARY_MAPPER, staffId);
    }

    /**
     * Optimistic status transition: only moves the row if it is still in
     * {@code from}. Returns false when a concurrent request already moved it.
     */
    public boolean updateStatus(UUID id, BookingStatus from, BookingStatus to) {
        return jdbc.update(
                "UPDATE bookings SET status = ? WHERE id = ? AND status = ? AND deleted_at IS NULL",
                to.name(), id, from.name()) == 1;
    }

    /** Booking-level payment progress, derived from the deposit/balance payment rows. */
    private static PaymentState paymentState(String depositStatus, String balanceStatus,
                                             BigDecimal price, BigDecimal depositAmount) {
        if (!"PAID".equals(depositStatus)) {
            return PaymentState.AWAITING_PAYMENT;
        }
        if (depositAmount.compareTo(price) == 0) {
            return PaymentState.PAID_IN_FULL; // paid in full up front
        }
        if ("PAID".equals(balanceStatus)) {
            return PaymentState.PAID_IN_FULL;
        }
        return PaymentState.DEPOSIT_PAID;
    }
}
