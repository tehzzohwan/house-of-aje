package com.houseofaje.staff;

/** Fields an admin supplies to create a staff member. Only name is required. */
public record CreateStaffRequest(
        String name,
        String email,
        String phone,
        String bio,
        String photoUrl) {}
