package com.houseofaje.payment;

import java.math.BigDecimal;

public record DepositRequest(
        String bookingReference,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String customerPhone) {}
