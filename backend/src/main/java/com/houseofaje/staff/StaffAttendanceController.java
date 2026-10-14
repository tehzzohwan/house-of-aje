package com.houseofaje.staff;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff clock in / clock out (attendance). */
@RestController
@RequestMapping("/api/staff/attendance")
public class StaffAttendanceController {

    private final StaffAttendanceService attendance;

    public StaffAttendanceController(StaffAttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping("/status")
    public AttendanceStatus status(Authentication auth) {
        return attendance.status(UUID.fromString(auth.getName()));
    }

    @PostMapping("/clock-in")
    public AttendanceStatus clockIn(Authentication auth) {
        return attendance.clockIn(UUID.fromString(auth.getName()));
    }

    @PostMapping("/clock-out")
    public AttendanceStatus clockOut(Authentication auth) {
        return attendance.clockOut(UUID.fromString(auth.getName()));
    }
}
