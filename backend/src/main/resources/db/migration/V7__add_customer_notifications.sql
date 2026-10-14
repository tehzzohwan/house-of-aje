-- In-app notifications: a customer-facing inbox, separate from the outbound
-- email/SMS delivery log (notifications). Rows are written on booking lifecycle
-- events and read back by the customer.
CREATE TABLE customer_notifications (
    id          BIGSERIAL PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    booking_id  UUID REFERENCES bookings(id),
    type        VARCHAR(64) NOT NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_notifications_customer
    ON customer_notifications (customer_id, created_at DESC);
