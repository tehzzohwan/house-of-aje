package com.houseofaje.customer;

import com.houseofaje.security.JwtService;
import com.houseofaje.security.LoginResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer self-service registration; login is handled by the unified {@code AuthService}. */
@Service
public class CustomerAccountService {

    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public CustomerAccountService(CustomerRepository customers, PasswordEncoder passwords, JwtService jwt) {
        this.customers = customers;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public LoginResponse register(CustomerRegisterRequest request) {
        if (isBlank(request.firstName()) || isBlank(request.lastName()) || isBlank(request.username())
                || isBlank(request.phone()) || isBlank(request.password())) {
            throw new IllegalArgumentException("firstName, lastName, username, phone and password are required");
        }
        String phone = request.phone().trim();
        String username = request.username().trim();
        String email = blankToNull(request.email());
        if (email != null && customers.registeredEmailExists(email)) {
            throw new IllegalArgumentException("An account already exists for this email");
        }
        if (customers.registeredPhoneExists(phone)) {
            throw new IllegalArgumentException("An account already exists for this phone");
        }
        if (customers.registeredUsernameExists(username)) {
            throw new IllegalArgumentException("This username is already taken");
        }
        String hash = passwords.encode(request.password());

        // Reuse a guest customer with the same phone so their earlier bookings
        // become visible to the new account.
        UUID id = customers.findByPhone(phone)
                .map(existing -> {
                    customers.promoteToAccount(existing.id(), request.firstName().trim(),
                            request.lastName().trim(), email, username, hash);
                    return existing.id();
                })
                .orElseGet(() -> {
                    UUID created = UUID.randomUUID();
                    customers.insert(created, request.firstName().trim(), request.lastName().trim(),
                            email, phone, username, hash);
                    return created;
                });
        return new LoginResponse(jwt.issue(id.toString(), List.of("ROLE_CUSTOMER")), "CUSTOMER");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
