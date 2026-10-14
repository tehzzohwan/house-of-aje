package com.houseofaje.payment;

public record DepositInstructions(
        String providerReference,
        String accountNumber,
        String accountName,
        String bankName) {}
