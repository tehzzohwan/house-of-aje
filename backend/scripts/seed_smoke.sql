-- Smoke-test reference data for the hoa_smoke database.
-- Idempotent: safe to re-run.

INSERT INTO services (id, name, slug, description, duration_minutes, price, buffer_minutes, active)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'Braiding', 'braiding', 'Cornrows, box braids and more.', 60, 10000.00, 0, TRUE),
  ('22222222-2222-2222-2222-222222222222', 'Weaving', 'weaving', 'Weave installation and styling.', 90, 15000.00, 0, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff (id, name, email, phone, active)
VALUES
  ('33333333-3333-3333-3333-333333333333', 'Ada',  'ada@houseofaje.test',  '08010000001', TRUE),
  ('44444444-4444-4444-4444-444444444444', 'Bisi', 'bisi@houseofaje.test', '08010000002', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff_services (staff_id, service_id)
VALUES
  ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111'),
  ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222'),
  ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111'),
  ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222')
ON CONFLICT (staff_id, service_id) DO NOTHING;

-- Working hours 09:00-18:00 every day, for both staff.
INSERT INTO staff_working_hours (staff_id, day_of_week, start_time, end_time)
SELECT s.id, d.dow, '09:00'::time, '18:00'::time
FROM staff s
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7)) AS d(dow)
ON CONFLICT DO NOTHING;
