-- Staff attendance (clock in / clock out). A staff member may clock in and out
-- several times during a day (e.g. a break); each interval is one row. The
-- partial unique index guarantees at most one open (not yet clocked-out) shift.
-- Admins do not clock in; only staff rows are expected here.
CREATE TABLE staff_attendance (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id     UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    clock_in_at  TIMESTAMPTZ NOT NULL,
    clock_out_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_attendance_staff ON staff_attendance (staff_id, clock_in_at);

CREATE UNIQUE INDEX idx_staff_attendance_one_open_shift
    ON staff_attendance (staff_id)
    WHERE clock_out_at IS NULL;

CREATE TRIGGER trg_staff_attendance_updated
    BEFORE UPDATE ON staff_attendance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
