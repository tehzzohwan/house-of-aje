package com.houseofaje.payment;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op provider (active unless {@code hoa.payment.provider=paystack}).
 * Lets the booking flow run without Paystack credentials; its webhook
 * verification always reports "not paid", so nothing is ever auto-confirmed.
 */
@Component
@ConditionalOnProperty(name = "hoa.payment.provider", havingValue = "stub", matchIfMissing = true)
public class StubPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "STUB";
    }

    @Override
    public DepositInstructions initiateDeposit(DepositRequest request) {
        return new DepositInstructions(
                "stub-" + request.bookingReference(),
                "0000000000",
                "House of Aje",
                "Stub Bank");
    }

    @Override
    public TransactionVerification verifyTransaction(String transactionReference) {
        return new TransactionVerification(false, transactionReference, BigDecimal.ZERO, "NGN", null, null);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return new RefundResult(true, "stub-refund-" + request.transactionReference());
    }
}
