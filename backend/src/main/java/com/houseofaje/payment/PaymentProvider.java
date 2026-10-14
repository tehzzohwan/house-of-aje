package com.houseofaje.payment;

/** Abstraction over payment providers: bank transfer now, card later. */
public interface PaymentProvider {

    /** Provider identifier stored on {@code payments.provider}. */
    String name();

    /**
     * Obtain a dedicated bank-transfer account for a booking's deposit.
     * Must be idempotent per {@code request.bookingReference()} — a virtual
     * account is stable for the lifetime of a booking.
     */
    DepositInstructions initiateDeposit(DepositRequest request);

    /** Fetch the authoritative status of a transaction from the provider. */
    TransactionVerification verifyTransaction(String transactionReference);

    /** Issue a refund for a prior transaction. */
    RefundResult refund(RefundRequest request);
}
