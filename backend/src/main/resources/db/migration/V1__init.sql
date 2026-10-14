-- ============================================================================
-- House of Aje — initial schema
-- Conventions (per product requirements):
--   * Money       -> NUMERIC(12,2), never floating point
--   * Timestamps  -> TIMESTAMPTZ (stored UTC), converted for display only
--   * Soft-delete -> deleted_at on bookings / customers / payments only
--     (these are financial records and are never hard-deleted)
--   * services / staff are deactivated via `active`, not soft-deleted
-- ============================================================================

-- gist support for scalar equality, required by the staff+time exclusion
-- constraint that guarantees no double-booking at the database layer.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- services
-- ---------------------------------------------------------------------------
CREATE TABLE services (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL UNIQUE,
    description      TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    price            NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    buffer_minutes   INTEGER NOT NULL DEFAULT 0 CHECK (buffer_minutes >= 0),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- staff
-- ---------------------------------------------------------------------------
CREATE TABLE staff (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) UNIQUE,
    phone      VARCHAR(64),
    bio        TEXT,
    photo_url  TEXT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- which services each staff member performs
CREATE TABLE staff_services (
    staff_id   UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (staff_id, service_id)
);

-- staff working hours. ISO day-of-week: 1=Monday .. 7=Sunday.
-- Multiple rows per day are allowed (split shifts); the availability engine
-- intersects the union of a day's ranges with business hours.
CREATE TABLE staff_working_hours (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id    UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_time < end_time)
);
CREATE INDEX idx_staff_working_hours_staff ON staff_working_hours (staff_id);

-- time-off exceptions for a staff member
CREATE TABLE staff_time_off (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id   UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (starts_at < ends_at)
);
CREATE INDEX idx_staff_time_off_staff_range ON staff_time_off (staff_id, starts_at, ends_at);

-- ---------------------------------------------------------------------------
-- customers
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_customers_phone ON customers (phone);

-- ---------------------------------------------------------------------------
-- bookings
-- ---------------------------------------------------------------------------
CREATE TABLE bookings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference      VARCHAR(32) NOT NULL UNIQUE,
    customer_id    UUID NOT NULL REFERENCES customers(id),
    service_id     UUID NOT NULL REFERENCES services(id),
    staff_id       UUID NOT NULL REFERENCES staff(id),
    start_at       TIMESTAMPTZ NOT NULL,
    end_at         TIMESTAMPTZ NOT NULL,
    status         VARCHAR(32) NOT NULL,
    price          NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    deposit_amount NUMERIC(12,2) NOT NULL CHECK (deposit_amount >= 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CHECK (start_at < end_at),
    CHECK (deposit_amount <= price),
    CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW','EXPIRED'))
);
CREATE INDEX idx_bookings_staff_range ON bookings (staff_id, start_at, end_at);
CREATE INDEX idx_bookings_customer    ON bookings (customer_id);
CREATE INDEX idx_bookings_service     ON bookings (service_id);
CREATE INDEX idx_bookings_status      ON bookings (status);

-- Core no-double-booking guarantee. A staff member cannot hold two bookings
-- whose time ranges overlap, UNLESS the earlier booking is CANCELLED, EXPIRED
-- or NO_SHOW (those release the slot). Postgres enforces this at the storage
-- layer, so no application-level race condition can ever create a double
-- booking. Application logic is a second line of defence, never the only one.
ALTER TABLE bookings
    ADD CONSTRAINT bookings_no_double_booking
    EXCLUDE USING gist (
        staff_id                          WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status NOT IN ('CANCELLED', 'EXPIRED', 'NO_SHOW'));

-- ---------------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id         UUID NOT NULL REFERENCES bookings(id),
    amount             NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    type               VARCHAR(32) NOT NULL CHECK (type IN ('DEPOSIT','BALANCE','REFUND')),
    status             VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','PAID','FAILED','REFUNDED')),
    provider           VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255),
    idempotency_key    VARCHAR(255) NOT NULL UNIQUE,
    currency           CHAR(3) NOT NULL DEFAULT 'NGN',
    paid_at            TIMESTAMPTZ,
    webhook_payload    JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);
CREATE INDEX idx_payments_booking ON payments (booking_id);

-- ---------------------------------------------------------------------------
-- waitlist
-- ---------------------------------------------------------------------------
CREATE TABLE waitlist (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL REFERENCES customers(id),
    service_id   UUID NOT NULL REFERENCES services(id),
    staff_id     UUID REFERENCES staff(id),
    requested_at TIMESTAMPTZ,
    status       VARCHAR(32) NOT NULL DEFAULT 'WAITING'
                 CHECK (status IN ('WAITING','NOTIFIED','BOOKED','CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_waitlist_status  ON waitlist (status);
CREATE INDEX idx_waitlist_service ON waitlist (service_id);

-- ---------------------------------------------------------------------------
-- reviews — one per booking; moderation hides/unhides, never deletes
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL UNIQUE REFERENCES bookings(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    status      VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED'
                CHECK (status IN ('PUBLISHED','HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_status_rating ON reviews (status, rating);

-- ---------------------------------------------------------------------------
-- audit_log — append-only
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    entity     VARCHAR(64) NOT NULL,
    entity_id  VARCHAR(64) NOT NULL,
    action     VARCHAR(64) NOT NULL,
    actor      VARCHAR(64),
    before     JSONB,
    after      JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log (entity, entity_id);

-- ---------------------------------------------------------------------------
-- notifications — append-only delivery log
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          UUID REFERENCES bookings(id),
    customer_id         UUID REFERENCES customers(id),
    channel             VARCHAR(16) NOT NULL CHECK (channel IN ('SMS','EMAIL')),
    type                VARCHAR(64) NOT NULL,
    recipient           VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts            INTEGER NOT NULL DEFAULT 0,
    last_error          TEXT,
    provider_message_id VARCHAR(255),
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_status ON notifications (status);

-- ---------------------------------------------------------------------------
-- updated_at trigger for all mutable tables
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_services_updated             BEFORE UPDATE ON services            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_staff_updated                BEFORE UPDATE ON staff               FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_staff_working_hours_updated  BEFORE UPDATE ON staff_working_hours FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_staff_time_off_updated       BEFORE UPDATE ON staff_time_off      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customers_updated            BEFORE UPDATE ON customers           FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_bookings_updated             BEFORE UPDATE ON bookings            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payments_updated             BEFORE UPDATE ON payments            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_waitlist_updated             BEFORE UPDATE ON waitlist            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_reviews_updated              BEFORE UPDATE ON reviews             FOR EACH ROW EXECUTE FUNCTION set_updated_at();
