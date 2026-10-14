package com.houseofaje.staff;

import com.houseofaje.security.JwtService;
import com.houseofaje.security.LoginResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff accounts are created from an admin-issued invite link; login is via the unified {@code AuthService}. */
@Service
public class StaffAccountService {

    private final StaffRepository staff;
    private final StaffInviteService invites;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public StaffAccountService(StaffRepository staff, StaffInviteService invites,
            PasswordEncoder passwords, JwtService jwt) {
        this.staff = staff;
        this.invites = invites;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public LoginResponse register(StaffInviteRequest request) {
        String name = blankToNull(request.name());
        if (name == null || isBlank(request.password())) {
            throw new IllegalArgumentException("name and password are required");
        }
        UUID staffId = invites.consume(blankToNull(request.token()));
        if (!staff.promoteToAccount(staffId, name, blankToNull(request.phone()), passwords.encode(request.password()))) {
            throw new IllegalArgumentException("This staff account is no longer active");
        }
        return new LoginResponse(jwt.issue(staffId.toString(), List.of("ROLE_STAFF")), "STAFF");
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
