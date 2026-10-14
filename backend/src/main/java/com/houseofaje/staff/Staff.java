package com.houseofaje.staff;

import java.util.UUID;

/** A staff member as seen in the admin console. */
public record Staff(
        UUID id,
        String name,
        String email,
        String phone,
        String bio,
        String photoUrl,
        boolean active) {}
