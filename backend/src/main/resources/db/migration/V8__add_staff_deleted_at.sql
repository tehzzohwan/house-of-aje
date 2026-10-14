-- Soft-delete for staff: admins can remove a staff member without breaking
-- booking history (bookings.staff_id is NOT NULL and has no cascade).
ALTER TABLE staff ADD COLUMN deleted_at TIMESTAMPTZ;
