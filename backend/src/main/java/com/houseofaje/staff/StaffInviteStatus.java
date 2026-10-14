package com.houseofaje.staff;

/** Whether a staff member has an outstanding, still-valid invite link. */
public record StaffInviteStatus(boolean issued, String token) {}
