package com.houseofaje.security;

import com.houseofaje.customer.CustomerRepository;
import com.houseofaje.staff.StaffRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Single login entry point for all roles, resolved from one identifier + password. */
@Service
public class AuthService {

    private final SecurityProperties props;
    private final StaffRepository staff;
    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(SecurityProperties props, StaffRepository staff, CustomerRepository customers,
            PasswordEncoder passwords, JwtService jwt) {
        this.props = props;
        this.staff = staff;
        this.customers = customers;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    public LoginResponse login(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String id = identifier.trim();

        // Admin is matched first by its configured username.
        if (constantTimeEquals(props.adminUsername(), id)) {
            if (!constantTimeEquals(props.adminPassword(), password)) {
                throw new BadCredentialsException("Invalid credentials");
            }
            return new LoginResponse(jwt.issue(props.adminUsername(), List.of("ROLE_ADMIN")), "ADMIN");
        }

        StaffRepository.AccountCredential staffCred = staff.findCredential(id).orElse(null);
        if (staffCred != null && passwords.matches(password, staffCred.passwordHash())) {
            return new LoginResponse(jwt.issue(staffCred.id().toString(), List.of("ROLE_STAFF")), "STAFF");
        }

        CustomerRepository.AccountCredential customerCred = customers.findCredential(id).orElse(null);
        if (customerCred != null && passwords.matches(password, customerCred.passwordHash())) {
            return new LoginResponse(jwt.issue(customerCred.id().toString(), List.of("ROLE_CUSTOMER")), "CUSTOMER");
        }

        throw new BadCredentialsException("Invalid credentials");
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
