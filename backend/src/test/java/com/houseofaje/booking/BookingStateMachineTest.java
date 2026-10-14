package com.houseofaje.booking;

import org.junit.jupiter.api.Test;

import static com.houseofaje.booking.BookingStatus.CANCELLED;
import static com.houseofaje.booking.BookingStatus.COMPLETED;
import static com.houseofaje.booking.BookingStatus.CONFIRMED;
import static com.houseofaje.booking.BookingStatus.EXPIRED;
import static com.houseofaje.booking.BookingStatus.NO_SHOW;
import static com.houseofaje.booking.BookingStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingStateMachineTest {

    @Test
    void pendingPaymentAllowsConfirmCancelAndExpire() {
        assertThat(BookingStateMachine.canTransition(PENDING_PAYMENT, CONFIRMED)).isTrue();
        assertThat(BookingStateMachine.canTransition(PENDING_PAYMENT, CANCELLED)).isTrue();
        assertThat(BookingStateMachine.canTransition(PENDING_PAYMENT, EXPIRED)).isTrue();
    }

    @Test
    void confirmedAllowsCompleteCancelAndNoShow() {
        assertThat(BookingStateMachine.canTransition(CONFIRMED, COMPLETED)).isTrue();
        assertThat(BookingStateMachine.canTransition(CONFIRMED, CANCELLED)).isTrue();
        assertThat(BookingStateMachine.canTransition(CONFIRMED, NO_SHOW)).isTrue();
    }

    @Test
    void terminalStatesAllowNothing() {
        for (BookingStatus terminal : new BookingStatus[]{COMPLETED, CANCELLED, NO_SHOW, EXPIRED}) {
            for (BookingStatus to : BookingStatus.values()) {
                assertThat(BookingStateMachine.canTransition(terminal, to))
                        .as("%s -> %s", terminal, to)
                        .isFalse();
            }
        }
    }

    @Test
    void noSelfTransition() {
        for (BookingStatus s : BookingStatus.values()) {
            assertThat(BookingStateMachine.canTransition(s, s)).isFalse();
        }
    }

    @Test
    void illegalTransitionIsRejected() {
        assertThat(BookingStateMachine.canTransition(PENDING_PAYMENT, COMPLETED)).isFalse();
        assertThat(BookingStateMachine.canTransition(PENDING_PAYMENT, NO_SHOW)).isFalse();
        assertThat(BookingStateMachine.canTransition(CONFIRMED, PENDING_PAYMENT)).isFalse();
    }

    @Test
    void requireTransitionThrowsOnIllegal() {
        assertThatThrownBy(() -> BookingStateMachine.requireTransition(PENDING_PAYMENT, COMPLETED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("PENDING_PAYMENT")
                .hasMessageContaining("COMPLETED");
    }
}
