package com.houseofaje.staff;

import com.houseofaje.availability.BusinessHours;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff clock in / clock out, worked-time aggregation, and the end-of-day auto clock-out. */
@Service
public class StaffAttendanceService {

    private final StaffAttendanceRepository attendance;
    private final StaffScheduleRepository schedule;
    private final BusinessHours businessHours;
    private final ZoneId businessZone;

    public StaffAttendanceService(
            StaffAttendanceRepository attendance,
            StaffScheduleRepository schedule,
            BusinessHours businessHours,
            @Value("${hoa.business-zone:Africa/Lagos}") String businessZone) {
        this.attendance = attendance;
        this.schedule = schedule;
        this.businessHours = businessHours;
        this.businessZone = ZoneId.of(businessZone);
    }

    public AttendanceStatus status(UUID staffId) {
        return attendance.openClockIn(staffId)
                .map(at -> new AttendanceStatus(true, at))
                .orElseGet(() -> new AttendanceStatus(false, null));
    }

    @Transactional
    public AttendanceStatus clockIn(UUID staffId) {
        if (attendance.isClockedIn(staffId)) {
            throw new IllegalArgumentException("Already clocked in");
        }
        attendance.clockIn(staffId, Instant.now());
        return status(staffId);
    }

    @Transactional
    public AttendanceStatus clockOut(UUID staffId) {
        if (!attendance.clockOut(staffId, Instant.now())) {
            throw new IllegalArgumentException("Not clocked in");
        }
        return status(staffId);
    }

    public long workedMinutesToday(UUID staffId) {
        LocalDate today = LocalDate.now(businessZone);
        return workedMinutesBetween(staffId, today.atStartOfDay(businessZone).toInstant(),
                today.plusDays(1).atStartOfDay(businessZone).toInstant());
    }

    /** Total minutes worked between {@code (from, to]}; an open shift is counted up to now. */
    public long workedMinutesBetween(UUID staffId, Instant from, Instant to) {
        long minutes = 0;
        Instant now = Instant.now();
        for (StaffAttendanceRepository.Interval i : attendance.intervals(staffId, from, to)) {
            Instant start = i.clockInAt().isBefore(from) ? from : i.clockInAt();
            Instant end = i.clockOutAt() == null ? now : i.clockOutAt();
            if (end.isAfter(to)) {
                end = to;
            }
            if (end.isAfter(start)) {
                minutes += Duration.between(start, end).toMinutes();
            }
        }
        return minutes;
    }

    /**
     * Closes any open shift whose scheduled day end has passed: at the staff's
     * own working-hours end for that weekday, falling back to business-hours end.
     */
    @Scheduled(fixedDelayString = "${hoa.attendance-clockout-interval:PT5M}")
    @Transactional
    public void autoClockOut() {
        Instant now = Instant.now();
        for (StaffAttendanceRepository.OpenShift shift : attendance.findOpenShifts()) {
            LocalDate day = shift.clockInAt().atZone(businessZone).toLocalDate();
            Optional<LocalTime> staffEnd = schedule.findLatestEnd(shift.staffId(), day.getDayOfWeek().getValue());
            LocalTime dayEnd = staffEnd.orElseGet(() -> businessHours.hoursFor(day.getDayOfWeek())
                    .map(BusinessHours.WorkPeriod::end).orElse(null));
            if (dayEnd == null) {
                continue;
            }
            Instant endInstant = day.atTime(dayEnd).atZone(businessZone).toInstant();
            if (!now.isBefore(endInstant)) {
                attendance.clockOut(shift.staffId(), endInstant);
            }
        }
    }
}
