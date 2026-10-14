package com.houseofaje.payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Authoritative transaction status fetched from the provider during webhook
 * processing. Amounts are in NGN (the provider adapter converts from kobo).
 */
public record TransactionVerification(
        boolean paid,
        String reference,
        BigDecimal amount,
        String currency,
        String customerCode,
        Instant paidAt) {}
