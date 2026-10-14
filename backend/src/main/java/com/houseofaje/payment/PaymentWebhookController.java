package com.houseofaje.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint (excluded from auth, like the rest of /api/public/**).
 * Returns 200 so Paystack stops retrying once a delivery is handled; a bad
 * signature returns 401 so Paystack retries / alerts.
 */
@RestController
@RequestMapping("/api/public/webhooks/paystack")
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader("x-paystack-signature") String signature,
            @RequestBody String rawBody) {
        webhookService.process(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
