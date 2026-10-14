-- Customer and staff self-service accounts. A non-null password_hash marks a
-- registered account; a null value is a guest customer or an admin-created
-- staff record that has not yet set up a login.
ALTER TABLE customers ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE staff ADD COLUMN password_hash VARCHAR(255);
