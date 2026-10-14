package com.houseofaje.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies Paystack's {@code x-paystack-signature}: HMAC-SHA512 of the raw
 * request body (UTF-8) keyed with the Paystack secret key, hex-encoded.
 * Comparison is constant-time to avoid leaking timing information.
 */
@Component
public class PaystackWebhookVerifier {

    private final byte[] secret;

    public PaystackWebhookVerifier(@Value("${hoa.paystack.secret-key:}") String secretKey) {
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = hmacSha512(secret, rawBody.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expected, hexToBytes(signature));
    }

    private static byte[] hmacSha512(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key, "HmacSHA512"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            return new byte[0];
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
