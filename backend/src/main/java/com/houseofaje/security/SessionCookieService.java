package com.houseofaje.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Issues and clears the session cookie that carries the JWT.
 *
 * <p>The token is stored in an HttpOnly cookie so JavaScript cannot read it, which
 * removes the XSS token-theft vector that session/local storage introduced. The
 * {@code SameSite=Lax} flag blocks the cookie from being sent on cross-site
 * subresource requests, which is the CSRF mitigation for this stateless API.
 *
 * <p>The {@code Secure} flag is off in local development (the frontend and backend
 * run over plain HTTP on localhost) and must be turned on in production by setting
 * {@code hoa.security.cookie-secure=true} under HTTPS.
 */
@Service
public class SessionCookieService {

    public static final String COOKIE_NAME = "hoa_session";

    private final boolean secure;
    private final long maxAgeSeconds;

    public SessionCookieService(
            SecurityProperties props,
            @Value("${hoa.security.cookie-secure:false}") boolean secure) {
        this.secure = secure;
        this.maxAgeSeconds = props.jwtTtl().toSeconds();
    }

    /** Builds the {@code Set-Cookie} header value for a freshly issued token. */
    public String setCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /** Builds a {@code Set-Cookie} header value that deletes the session cookie. */
    public String clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build()
                .toString();
    }

    /** Extracts the session token from the request cookie, or {@code null} if absent. */
    public String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
