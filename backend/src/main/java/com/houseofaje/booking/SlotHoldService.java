package com.houseofaje.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed slot hold with a TTL matched to the payment window.
 *
 * <p>The database exclusion constraint is the source of truth for no
 * double-booking; this hold is the payment-window timer. A {@code PENDING_PAYMENT}
 * booking holds its slot in the DB while this key is live, and the key's expiry
 * is the signal the reconciliation sweep uses to expire stale bookings. All
 * operations are fault-tolerant: Redis is an optimization, never a dependency.</p>
 */
@Service
public class SlotHoldService {

    private static final String PREFIX = "hoa:hold:booking:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SlotHoldService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void hold(UUID bookingId, UUID staffId, Instant start, Instant end, Duration ttl) {
        try {
            HoldRecord record = new HoldRecord(staffId, start, end);
            redis.opsForValue().set(PREFIX + bookingId, objectMapper.writeValueAsString(record), ttl);
        } catch (Exception ignored) {
            // Redis unavailable: the DB exclusion constraint still protects the slot.
        }
    }

    public boolean isHeld(UUID bookingId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + bookingId));
        } catch (Exception e) {
            return false;
        }
    }

    public void release(UUID bookingId) {
        try {
            redis.delete(PREFIX + bookingId);
        } catch (Exception ignored) {
            // best-effort; the key also expires on its own TTL
        }
    }

    private record HoldRecord(UUID staffId, Instant start, Instant end) {}
}
