package com.houseofaje.staff;

import java.util.List;
import java.util.UUID;

/** A staff member as shown to customers while booking. */
public record PublicStaff(
        UUID id,
        String name,
        String bio,
        String photoUrl,
        List<String> services,
        boolean available) {}
