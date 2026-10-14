-- Usernames let customers log in with something other than email or phone.
-- Nullable: guest customers (booked before registering) have none.
ALTER TABLE customers ADD COLUMN username VARCHAR(64);
CREATE UNIQUE INDEX idx_customers_username ON customers (username) WHERE username IS NOT NULL;
