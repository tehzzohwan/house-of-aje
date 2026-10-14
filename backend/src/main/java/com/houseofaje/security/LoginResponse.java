package com.houseofaje.security;

/** Login/registration result: the JWT plus the role the token was issued for. */
public record LoginResponse(String token, String role) {}
