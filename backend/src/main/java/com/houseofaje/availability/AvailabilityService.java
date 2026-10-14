package com.houseofaje.availability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Computes bookable slots for a service on a date.
 *
 * <p>Working window = business hours &cap; staff hours, minus time-off. Occupied
 * intervals = existing slot-holding bookings each expanded by that booking's
 * service buffer. The occupied intervals are cached per staff/day in Redis and
 * invalidated on booking mutation via {@link #evict(UUID, LocalDate)}.</p>
 */
@Service
public class AvailabilityService {

    private static final Duration SLOT_STEP = Duration.ofMinutes(15);
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final JdbcTemplate jdbc;
    private final BusinessHours businessHours;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ZoneId businessZone;

    public AvailabilityService(
            JdbcTemplate jdbc,
            BusinessHours businessHours,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${hoa.business-zone:Africa/Lagos}") String businessZone) {
        this.jdbc = jdbc;
        this.businessHours = businessHours;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.businessZone = ZoneId.of(businessZone);
    }

    public List<AvailabilitySlot> availableSlots(UUID serviceId, UUID staffId, LocalDate date) {
        ServiceInfo service = loadService(serviceId);
        if (service == null) {
            return List.of();
        }

        List<StaffRef> staff = staffForService(serviceId);
        if (staffId != null) {
            staff = staff.stream().filter(s -> s.id().equals(staffId)).toList();
        }

        Duration duration = Duration.ofMinutes(service.durationMinutes());
        Duration buffer = Duration.ofMinutes(service.bufferMinutes());

        List<AvailabilitySlot> slots = new ArrayList<>();
        for (StaffRef member : staff) {
            List<TimeInterval> working = workingWindow(member.id(), date);
            List<TimeInterval> occupied = occupiedIntervals(member.id(), date);
            for (LocalDateTime start : SlotMath.availableSlotStarts(working, occupied, duration, buffer, SLOT_STEP)) {
                slots.add(new AvailabilitySlot(toInstant(start), toInstant(start.plus(duration)), member.id(), member.name()));
            }
        }
        // Same-day availability must exclude slots whose start has already passed:
        // the calendar tracks the actual current time rather than the whole day.
        return dropPastSlots(slots, date);
    }

    /**
     * Application-level slot check (second line of defence; the database
     * exclusion constraint is the source of truth). True only if the staff
     * performs the service, the slot lies inside their working window, and
     * neither the slot nor its trailing buffer collides with an occupied
     * interval.
     */
    public boolean isSlotBookable(UUID serviceId, UUID staffId, Instant start, Instant end) {
        ServiceInfo service = loadService(serviceId);
        if (service == null || staffForService(serviceId).stream().noneMatch(s -> s.id().equals(staffId))) {
            return false;
        }
        LocalDateTime localStart = start.atZone(businessZone).toLocalDateTime();
        LocalDateTime localEnd = end.atZone(businessZone).toLocalDateTime();
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return false;
        }
        LocalDate date = localStart.toLocalDate();
        if (date.equals(LocalDate.now(businessZone)) && !start.isAfter(Instant.now())) {
            return false;
        }
        List<TimeInterval> working = SlotMath.normalize(workingWindow(staffId, date));
        boolean withinWorking = working.stream().anyMatch(w ->
                !localStart.isBefore(w.start()) && !localEnd.isAfter(w.end()));
        if (!withinWorking) {
            return false;
        }
        TimeInterval slot = new TimeInterval(localStart, localEnd.plus(Duration.ofMinutes(service.bufferMinutes())));
        return SlotMath.intersect(List.of(slot), occupiedIntervals(staffId, date)).isEmpty();
    }

    /** Evict the cached availability for a staff/day (call on booking mutation). */
    public void evict(UUID staffId, LocalDate date) {
        try {
            redis.delete(cacheKey(staffId, date));
        } catch (Exception ignored) {
            // Redis is an optimization; never let cache plumbing fail the mutation.
        }
    }

    /** Drop same-day slots whose start time has already passed. */
    private List<AvailabilitySlot> dropPastSlots(List<AvailabilitySlot> slots, LocalDate date) {
        if (!date.equals(LocalDate.now(businessZone))) {
            return slots;
        }
        Instant now = Instant.now();
        return slots.stream().filter(s -> s.start().isAfter(now)).toList();
    }

    // ------------------------------------------------------------------
    // Working window: business hours ∩ staff hours, minus time-off
    // ------------------------------------------------------------------

    private List<TimeInterval> workingWindow(UUID staffId, LocalDate date) {
        var business = businessHours.hoursFor(date.getDayOfWeek());
        if (business.isEmpty()) {
            return List.of();
        }
        List<TimeInterval> staff = staffHours(staffId, date);
        if (staff.isEmpty()) {
            return List.of();
        }
        TimeInterval businessInterval = new TimeInterval(
                date.atTime(business.get().start()), date.atTime(business.get().end()));
        List<TimeInterval> working = SlotMath.intersect(List.of(businessInterval), staff);
        return SlotMath.subtract(working, timeOffIntervals(staffId, date));
    }

    private List<TimeInterval> staffHours(UUID staffId, LocalDate date) {
        return jdbc.query(
                "SELECT start_time, end_time FROM staff_working_hours WHERE staff_id = ? AND day_of_week = ?",
                (rs, n) -> new TimeInterval(
                        rs.getTime("start_time").toLocalTime().atDate(date),
                        rs.getTime("end_time").toLocalTime().atDate(date)),
                staffId,
                date.getDayOfWeek().getValue());
    }

    private List<TimeInterval> timeOffIntervals(UUID staffId, LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(businessZone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(businessZone).toOffsetDateTime();
        return jdbc.query(
                "SELECT starts_at, ends_at FROM staff_time_off WHERE staff_id = ? AND starts_at < ? AND ends_at > ?",
                (rs, n) -> new TimeInterval(
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant().atZone(businessZone).toLocalDateTime(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant().atZone(businessZone).toLocalDateTime()),
                staffId,
                dayEnd,
                dayStart);
    }

    // ------------------------------------------------------------------
    // Occupied intervals: slot-holding bookings expanded by their service buffer
    // ------------------------------------------------------------------

    private List<TimeInterval> occupiedIntervals(UUID staffId, LocalDate date) {
        String key = cacheKey(staffId, date);
        List<TimeInterval> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<TimeInterval> occupied = queryOccupiedIntervals(staffId, date);
        writeCache(key, occupied);
        return occupied;
    }

    private List<TimeInterval> queryOccupiedIntervals(UUID staffId, LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(businessZone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(businessZone).toOffsetDateTime();
        return jdbc.query(
                """
                SELECT b.start_at, b.end_at, s.buffer_minutes
                FROM bookings b
                JOIN services s ON s.id = b.service_id
                WHERE b.staff_id = ?
                  AND b.status NOT IN ('CANCELLED', 'EXPIRED', 'NO_SHOW')
                  AND b.start_at < ? AND b.end_at > ?
                """,
                (rs, n) -> {
                    LocalDateTime start = rs.getObject("start_at", OffsetDateTime.class).toInstant().atZone(businessZone).toLocalDateTime();
                    LocalDateTime end = rs.getObject("end_at", OffsetDateTime.class).toInstant().atZone(businessZone).toLocalDateTime();
                    int bufferMinutes = rs.getInt("buffer_minutes");
                    return new TimeInterval(start, end.plusMinutes(bufferMinutes));
                },
                staffId,
                dayEnd,
                dayStart);
    }

    // ------------------------------------------------------------------
    // Cache helpers (fault-tolerant: Redis is an optimization, never a dependency)
    // ------------------------------------------------------------------

    private String cacheKey(UUID staffId, LocalDate date) {
        return "hoa:availability:staff:" + staffId + ":date:" + date;
    }

    private List<TimeInterval> readCache(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return null; // treat any Redis/serialization failure as a miss
        }
    }

    private void writeCache(String key, List<TimeInterval> occupied) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(occupied), CACHE_TTL);
        } catch (Exception ignored) {
            // cache write failure is non-fatal
        }
    }

    // ------------------------------------------------------------------
    // Data helpers
    // ------------------------------------------------------------------

    private ServiceInfo loadService(UUID serviceId) {
        List<ServiceInfo> rows = jdbc.query(
                "SELECT duration_minutes, buffer_minutes FROM services WHERE id = ? AND active = TRUE",
                (rs, n) -> new ServiceInfo(rs.getInt("duration_minutes"), rs.getInt("buffer_minutes")),
                serviceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<StaffRef> staffForService(UUID serviceId) {
        return jdbc.query("""
                SELECT st.id, st.name
                FROM staff st
                JOIN staff_services ss ON ss.staff_id = st.id
                WHERE ss.service_id = ?
                ORDER BY st.name
                """,
                (rs, n) -> new StaffRef(rs.getObject("id", UUID.class), rs.getString("name")),
                serviceId);
    }

    private Instant toInstant(LocalDateTime local) {
        return local.atZone(businessZone).toInstant();
    }

    private record ServiceInfo(int durationMinutes, int bufferMinutes) {}

    private record StaffRef(UUID id, String name) {}
}
