-- Manual verification of the no-double-booking exclusion constraint.
-- Run against a migrated database:
--   psql "$DATABASE_URL" -f backend/scripts/verify_exclusion.sql
--
-- Uses fixed IDs and arbitrary (60-min) time windows so the run is
-- deterministic and re-runnable.

INSERT INTO services (id, name, slug, duration_minutes, price, buffer_minutes)
VALUES ('00000000-0000-0000-0000-000000000001', 'Braiding', 'braiding', 60, 10000.00, 15)
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff (id, name)
VALUES ('00000000-0000-0000-0000-000000000002', 'Ada')
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff (id, name)
VALUES ('00000000-0000-0000-0000-000000000004', 'Bisi')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customers (id, first_name, last_name, phone)
VALUES ('00000000-0000-0000-0000-000000000003', 'Test', 'User', '08000000000')
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
    -- 1. A confirmed booking for Ada at 10:00-11:00 must succeed.
    INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
    VALUES ('VERIFY-A', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
            '2026-01-12T10:00:00Z', '2026-01-12T11:00:00Z', 'CONFIRMED', 10000.00, 4000.00);
    RAISE NOTICE 'PASS: first booking inserted';

    -- 2. An overlapping booking for the SAME staff (10:30-11:30) must be rejected.
    BEGIN
        INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
        VALUES ('VERIFY-B', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                '2026-01-12T10:30:00Z', '2026-01-12T11:30:00Z', 'CONFIRMED', 10000.00, 4000.00);
        RAISE EXCEPTION 'FAIL: overlapping booking was allowed';
    EXCEPTION WHEN exclusion_violation THEN
        RAISE NOTICE 'PASS: overlapping booking rejected (exclusion_violation)';
    END;

    -- 3. Same slot for a DIFFERENT staff member must be allowed.
    INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
    VALUES ('VERIFY-D', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004',
            '2026-01-12T10:00:00Z', '2026-01-12T11:00:00Z', 'CONFIRMED', 10000.00, 4000.00);
    RAISE NOTICE 'PASS: same slot allowed for a different staff member';

    -- 4. Back-to-back (11:00-12:00) must be allowed — '[)' range semantics.
    INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
    VALUES ('VERIFY-C', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004',
            '2026-01-12T11:00:00Z', '2026-01-12T12:00:00Z', 'CONFIRMED', 10000.00, 4000.00);
    RAISE NOTICE 'PASS: back-to-back booking allowed';

    -- 5. Cancelling the first booking releases its slot.
    UPDATE bookings SET status = 'CANCELLED' WHERE reference = 'VERIFY-A';

    BEGIN
        INSERT INTO bookings (reference, customer_id, service_id, staff_id, start_at, end_at, status, price, deposit_amount)
        VALUES ('VERIFY-E', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                '2026-01-12T10:30:00Z', '2026-01-12T11:30:00Z', 'CONFIRMED', 10000.00, 4000.00);
        RAISE NOTICE 'PASS: cancelled booking no longer blocks its slot';
    EXCEPTION WHEN exclusion_violation THEN
        RAISE EXCEPTION 'FAIL: cancelled booking still blocks the slot';
    END;

    RAISE NOTICE 'ALL EXCLUSION-CONSTRAINT CHECKS PASSED';
END $$;
