package com.houseofaje.booking;

import java.util.Map;
import java.util.Set;

/**
 * Encodes the legal booking lifecycle:
 *
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED | CANCELLED | EXPIRED
 * CONFIRMED       -> COMPLETED | CANCELLED | NO_SHOW
 * COMPLETED / CANCELLED / NO_SHOW / EXPIRED are terminal
 * </pre>
 */
public final class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.of(
            BookingStatus.PENDING_PAYMENT, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW));

    private BookingStateMachine() {}

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return from != to && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(BookingStatus from, BookingStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }
}
