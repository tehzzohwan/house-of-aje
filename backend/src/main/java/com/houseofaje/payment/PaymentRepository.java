package com.houseofaje.payment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Payment> MAPPER = (rs, n) -> new Payment(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getBigDecimal("amount"),
            PaymentType.valueOf(rs.getString("type")),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("provider"),
            rs.getString("provider_reference"),
            rs.getString("provider_transaction_reference"),
            rs.getString("idempotency_key"),
            rs.getString("currency"),
            toInstant(rs, "paid_at"));

    public void insert(Payment p) {
        jdbc.update("""
                INSERT INTO payments (id, booking_id, amount, type, status, provider, provider_reference, provider_transaction_reference, idempotency_key, currency, paid_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                p.id(), p.bookingId(), p.amount(), p.type().name(), p.status().name(),
                p.provider(), p.providerReference(), p.providerTransactionReference(), p.idempotencyKey(), p.currency(),
                p.paidAt() == null ? null : OffsetDateTime.ofInstant(p.paidAt(), ZoneOffset.UTC));
    }

    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("SELECT * FROM payments WHERE idempotency_key = ? AND deleted_at IS NULL", MAPPER, idempotencyKey)
                .stream().findFirst();
    }

    public Optional<Payment> findByProviderReference(String providerReference) {
        return jdbc.query("SELECT * FROM payments WHERE provider_reference = ? AND deleted_at IS NULL", MAPPER, providerReference)
                .stream().findFirst();
    }

    public Optional<Payment> findDepositForBooking(UUID bookingId) {
        return jdbc.query("SELECT * FROM payments WHERE booking_id = ? AND type = 'DEPOSIT' AND deleted_at IS NULL",
                        MAPPER, bookingId)
                .stream().findFirst();
    }

    public Optional<Payment> findBalanceForBooking(UUID bookingId) {
        return jdbc.query("SELECT * FROM payments WHERE booking_id = ? AND type = 'BALANCE' AND deleted_at IS NULL",
                        MAPPER, bookingId)
                .stream().findFirst();
    }

    /**
     * The pending payment awaiting a transfer on this provider reference. Because
     * a provider virtual account can be reused across payments for one customer,
     * matching only still-pending rows avoids mistaking an already-paid payment
     * (e.g. the deposit) for the one the webhook is reporting (e.g. the balance).
     */
    public Optional<Payment> findPendingByProviderReference(String providerReference) {
        return jdbc.query(
                "SELECT * FROM payments WHERE provider_reference = ? AND status = 'PENDING' AND deleted_at IS NULL "
                        + "ORDER BY created_at",
                MAPPER, providerReference).stream().findFirst();
    }

    public void updateProviderReference(UUID id, String providerReference) {
        jdbc.update("UPDATE payments SET provider_reference = ? WHERE id = ?", providerReference, id);
    }

    public void updateStatus(UUID id, PaymentStatus status) {
        jdbc.update("UPDATE payments SET status = ? WHERE id = ?", status.name(), id);
    }

    public void updateStatus(UUID id, PaymentStatus status, Instant paidAt) {
        jdbc.update("UPDATE payments SET status = ?, paid_at = ? WHERE id = ?",
                status.name(), OffsetDateTime.ofInstant(paidAt, ZoneOffset.UTC), id);
    }

    /**
     * Idempotent {@code PENDING -> PAID}: returns true only when this call
     * performed the transition (a redelivered webhook returns false). Also
     * captures the provider transaction reference for later refunds.
     */
    public boolean markPaid(UUID id, Instant paidAt, String transactionReference, String webhookPayload) {
        return jdbc.update(
                "UPDATE payments SET status = 'PAID', paid_at = ?, provider_transaction_reference = ?, webhook_payload = ?::jsonb "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL",
                paidAt == null ? null : OffsetDateTime.ofInstant(paidAt, ZoneOffset.UTC),
                transactionReference,
                webhookPayload,
                id) == 1;
    }

    /** Idempotent manual PENDING -> PAID (cash or off-webhook deposit). */
    public boolean markPaidManual(UUID id, Instant paidAt) {
        return jdbc.update(
                "UPDATE payments SET status = 'PAID', paid_at = ? WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL",
                OffsetDateTime.ofInstant(paidAt, ZoneOffset.UTC), id) == 1;
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
