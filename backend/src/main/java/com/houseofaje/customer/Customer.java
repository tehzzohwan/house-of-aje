package com.houseofaje.customer;

import java.time.Instant;
import java.util.UUID;

public record Customer(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String username,
        Instant createdAt) {}
