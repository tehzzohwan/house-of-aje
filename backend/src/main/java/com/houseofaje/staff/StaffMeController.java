package com.houseofaje.staff;

import com.houseofaje.booking.BookingRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The logged-in staff member's own profile view. */
@RestController
@RequestMapping("/api/staff")
public class StaffMeController {

    private final StaffRepository staff;
    private final StaffScheduleRepository schedule;
    private final BookingRepository bookings;
    private final StaffAttendanceService attendance;

    public StaffMeController(StaffRepository staff, StaffScheduleRepository schedule, BookingRepository bookings,
            StaffAttendanceService attendance) {
        this.staff = staff;
        this.schedule = schedule;
        this.bookings = bookings;
        this.attendance = attendance;
    }

    @GetMapping("/me")
    public StaffProfile me(Authentication auth) {
        UUID id = UUID.fromString(auth.getName());
        Staff s = staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
        if (!s.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff account is deactivated");
        }
        AttendanceStatus status = attendance.status(id);
        return new StaffProfile(
                s.id(), s.name(), s.email(), s.phone(), s.bio(), s.photoUrl(), s.active(),
                schedule.findServiceNames(id),
                schedule.findHours(id),
                bookings.findByStaff(id),
                status.clockedIn(),
                attendance.workedMinutesToday(id));
    }
}
