-- Multi-item checkout, reservations, gateway payment audit, refunds (metadata).
-- Inventory: stock_on_hand NULL = unlimited; stock_reserved holds soft reservations.

ALTER TABLE products
    ADD COLUMN stock_on_hand INT NULL COMMENT 'NULL = unlimited inventory' AFTER active,
    ADD COLUMN stock_reserved INT NOT NULL DEFAULT 0 AFTER stock_on_hand;

CREATE TABLE checkout_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    merchant_order_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    total_amount DECIMAL(14, 2) NOT NULL,
    pricing_snapshot_hash VARCHAR(128) NULL,
    customer_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(50) NOT NULL,
    delivery_address TEXT NOT NULL,
    checkout_idempotency_key VARCHAR(64) NULL,
    payment_init_idempotency_key VARCHAR(64) NULL,
    reservation_expires_at DATETIME(6) NULL,
    paid_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    fail_reason VARCHAR(500) NULL,
    refund_total_amount DECIMAL(14, 2) NULL,
    refund_note VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkout_merchant_order (merchant_order_id),
    UNIQUE KEY uk_checkout_user_checkout_idem (user_id, checkout_idempotency_key),
    UNIQUE KEY uk_checkout_user_payinit_idem (user_id, payment_init_idempotency_key),
    KEY idx_checkout_status_resv (status, reservation_expires_at),
    KEY idx_checkout_user_created (user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE checkout_order_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_snapshot DECIMAL(14, 2) NOT NULL,
    line_total DECIMAL(14, 2) NOT NULL,
    fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_col_order (order_id),
    KEY idx_col_product (product_id),
    CONSTRAINT fk_col_order FOREIGN KEY (order_id) REFERENCES checkout_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE checkout_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cr_line_status (order_line_id, status),
    KEY idx_cr_expires (status, expires_at),
    KEY idx_cr_product (product_id),
    CONSTRAINT fk_cr_line FOREIGN KEY (order_line_id) REFERENCES checkout_order_lines (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE checkout_gateway_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    tracking_id VARCHAR(120) NOT NULL,
    gateway_order_id VARCHAR(120) NULL,
    amount DECIMAL(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_mode VARCHAR(50) NULL,
    status_message VARCHAR(500) NULL,
    raw_response_enc TEXT NULL,
    raw_response_dec TEXT NULL,
    client_ip VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cgp_tracking (tracking_id),
    KEY idx_cgp_order (order_id),
    CONSTRAINT fk_cgp_order FOREIGN KEY (order_id) REFERENCES checkout_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE checkout_payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NULL,
    merchant_order_id VARCHAR(64) NULL,
    event_type VARCHAR(40) NOT NULL,
    payload TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cpe_order (order_id),
    KEY idx_cpe_merchant (merchant_order_id)
) ENGINE=InnoDB;

CREATE TABLE checkout_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL,
    gateway_reference VARCHAR(120) NULL,
    raw_response TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_cr_order (order_id),
    CONSTRAINT fk_cr_order_ref FOREIGN KEY (order_id) REFERENCES checkout_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB;
