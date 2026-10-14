package com.houseofaje.security;

/** A single login for every role; {@code identifier} is the admin username, staff email, or customer email/phone. */
public record LoginRequest(String identifier, String password) {}
