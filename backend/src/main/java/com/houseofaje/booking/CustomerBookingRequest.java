package com.houseofaje.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * A booking placed by the authenticated customer. The customer is derived from
 * the session, not trusted from the body.
 */
public record CustomerBookingRequest(UUID serviceId, UUID staffId, Instant startAt, boolean payFull) {}
