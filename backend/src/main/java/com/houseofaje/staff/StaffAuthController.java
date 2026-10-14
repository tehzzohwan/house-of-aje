package com.houseofaje.staff;

import com.houseofaje.security.AuthResponse;
import com.houseofaje.security.LoginResponse;
import com.houseofaje.security.SessionCookieService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/staff")
public class StaffAuthController {

    private final StaffAccountService accounts;
    private final SessionCookieService cookies;

    public StaffAuthController(StaffAccountService accounts, SessionCookieService cookies) {
        this.accounts = accounts;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody StaffInviteRequest request, HttpServletResponse response) {
        LoginResponse result = accounts.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.setCookie(result.token()));
        return new AuthResponse(result.role());
    }
}
