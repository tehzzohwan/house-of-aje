package com.houseofaje.customer;

public record CustomerRegisterRequest(
        String firstName, String lastName, String username, String email, String phone, String password) {}
