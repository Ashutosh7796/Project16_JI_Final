CREATE TABLE IF NOT EXISTS application_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    category VARCHAR(30) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_label VARCHAR(255) NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(128) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    details TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_app_audit_created (created_at),
    KEY idx_app_audit_category_action (category, action_type),
    KEY idx_app_audit_actor (actor_user_id)
);
