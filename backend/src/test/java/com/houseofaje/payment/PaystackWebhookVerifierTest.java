package com.houseofaje.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackWebhookVerifierTest {

    private static final String SECRET = "sk_test_secret";
    private final PaystackWebhookVerifier verifier = new PaystackWebhookVerifier(SECRET);

    @Test
    void acceptsValidSignature() {
        String body = "{\"event\":\"charge.success\"}";
        assertThat(verifier.verify(body, sign(body))).isTrue();
    }

    @Test
    void rejectsSignatureForDifferentBody() {
        String signed = sign("{\"event\":\"charge.success\",\"amount\":400000}");
        assertThat(verifier.verify("{\"event\":\"charge.success\",\"amount\":400001}", signed)).isFalse();
    }

    @Test
    void rejectsSignatureFromDifferentSecret() {
        String body = "{\"event\":\"charge.success\"}";
        String foreign = signWith(body, "sk_other_secret");
        assertThat(verifier.verify(body, foreign)).isFalse();
    }

    @Test
    void rejectsMissingOrBlankSignature() {
        assertThat(verifier.verify("body", null)).isFalse();
        assertThat(verifier.verify("body", "   ")).isFalse();
    }

    @Test
    void rejectsMalformedHexSignature() {
        assertThat(verifier.verify("body", "not-a-hex-signature")).isFalse();
    }

    private static String sign(String body) {
        return signWith(body, SECRET);
    }

    private static String signWith(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
