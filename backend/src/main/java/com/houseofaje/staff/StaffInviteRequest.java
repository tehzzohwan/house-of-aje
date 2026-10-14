package com.houseofaje.staff;

/** A staff registration completed through an invite link; the email comes from the staff record. */
public record StaffInviteRequest(String token, String name, String phone, String password) {}
