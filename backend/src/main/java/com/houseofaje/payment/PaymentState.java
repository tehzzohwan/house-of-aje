package com.houseofaje.payment;

/**
 * Booking-level payment progress, distinct from the booking's own lifecycle
 * status. Derived from the booking's deposit and balance payment rows rather
 * than stored as a column.
 */
public enum PaymentState {
    /** Deposit not yet paid. */
    AWAITING_PAYMENT,
    /** Deposit paid, balance still outstanding. */
    DEPOSIT_PAID,
    /** Full price received — up front, or after the balance settled. */
    PAID_IN_FULL;
}
