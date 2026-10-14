-- The Paystack transaction reference is known only when a bank transfer lands
-- (it arrives on the charge.success webhook, not at deposit-initiation time).
-- It is needed to issue a refund against the original transaction.
ALTER TABLE payments ADD COLUMN provider_transaction_reference VARCHAR(255);
