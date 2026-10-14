package com.houseofaje.security;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void issuesAndVerifiesRoundTrip() {
        JwtService jwt = service(Duration.ofHours(1));

        String token = jwt.issue("admin", List.of("ROLE_ADMIN"));
        var decoded = jwt.verify(token);

        assertThat(decoded.getSubject()).isEqualTo("admin");
        assertThat(decoded.getClaimAsStringList("authorities")).containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsGarbageToken() {
        JwtService jwt = service(Duration.ofHours(1));

        assertThatThrownBy(() -> jwt.verify("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtService issuer = service(Duration.ofHours(1));
        JwtService verifier = new JwtService(new SecurityProperties(
                "another-secret-another-secret-another-secret!", Duration.ofHours(1), "admin", "admin"));

        String token = issuer.issue("admin", List.of("ROLE_ADMIN"));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    private static JwtService service(Duration ttl) {
        return new JwtService(new SecurityProperties(SECRET, ttl, "admin", "admin"));
    }
}
