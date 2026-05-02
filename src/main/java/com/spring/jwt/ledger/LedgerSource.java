package com.spring.jwt.ledger;

public enum LedgerSource {
    /** Direct API call (e.g. customer-initiated payment) */
    API,
    /** Gateway webhook/callback */
    WEBHOOK,
    /** Admin manual action */
    ADMIN,
    /** Scheduled reconciliation job */
    RECONCILIATION,
    /** System-generated (e.g. late-success recovery) */
    SYSTEM
}
