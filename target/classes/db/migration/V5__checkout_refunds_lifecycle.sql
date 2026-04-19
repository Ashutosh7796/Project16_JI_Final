-- Refund lifecycle: retries, admin override, evidence, gateway correlation.

ALTER TABLE checkout_refunds
    ADD COLUMN cca_tracking_id VARCHAR(120) NULL COMMENT 'Original CCAvenue tracking_id / reference_no for refund API' AFTER gateway_reference,
    ADD COLUMN request_payload TEXT NULL AFTER raw_response,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN last_checked_at DATETIME(6) NULL AFTER retry_count,
    ADD COLUMN is_manual TINYINT(1) NOT NULL DEFAULT 0 AFTER last_checked_at,
    ADD COLUMN admin_id BIGINT NULL AFTER is_manual,
    ADD COLUMN admin_notes TEXT NULL AFTER admin_id,
    ADD COLUMN support_ticket_id VARCHAR(120) NULL AFTER admin_notes,
    ADD COLUMN bank_reference VARCHAR(120) NULL AFTER support_ticket_id;

UPDATE checkout_refunds SET status = 'SUCCESS' WHERE status = 'COMPLETED';
UPDATE checkout_refunds SET status = 'PENDING_GATEWAY' WHERE status = 'REQUESTED';

CREATE TABLE checkout_refund_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_id BIGINT NOT NULL,
    admin_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    details TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cral_refund (refund_id),
    CONSTRAINT fk_cral_refund FOREIGN KEY (refund_id) REFERENCES checkout_refunds (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cr_status_retry ON checkout_refunds (status, last_checked_at, retry_count);
