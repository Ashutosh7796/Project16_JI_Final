-- Opaque token for polling callback status (prevents sequential queue id enumeration).
-- Backfill uses primary key to guarantee uniqueness across MySQL versions.

ALTER TABLE payment_callback_queue
    ADD COLUMN status_token VARCHAR(64) NULL;

UPDATE payment_callback_queue
SET status_token = CONCAT(
        'm',
        LPAD(HEX(id), 16, '0'),
        REPLACE(UUID(), '-', '')
    )
WHERE status_token IS NULL
   OR status_token = '';

ALTER TABLE payment_callback_queue
    MODIFY COLUMN status_token VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX uk_pay_cbq_status_token ON payment_callback_queue (status_token);
