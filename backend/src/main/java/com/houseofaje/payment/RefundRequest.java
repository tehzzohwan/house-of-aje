package com.houseofaje.payment;

import java.math.BigDecimal;

public record RefundRequest(String transactionReference, BigDecimal amount, String currency) {}
