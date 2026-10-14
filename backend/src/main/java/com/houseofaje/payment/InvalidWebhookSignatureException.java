package com.houseofaje.payment;

public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException() {
        super("Webhook signature verification failed");
    }
}
