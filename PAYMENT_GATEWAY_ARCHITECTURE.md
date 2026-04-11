# Payment Gateway Integration Architecture

## Executive Summary

This document provides a comprehensive overview of the CCAvenue payment gateway integration implemented in the JioJi Green India application. The architecture supports two distinct payment flows: **Product Purchase Payments** and **Farmer Registration Payments**, with robust security, fraud prevention, idempotency, and asynchronous callback processing.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Components](#architecture-components)
3. [Payment Flows](#payment-flows)
4. [Security Architecture](#security-architecture)
5. [Edge Cases & Challenges](#edge-cases--challenges)
6. [Database Schema](#database-schema)
7. [Configuration](#configuration)
8. [Monitoring & Auditing](#monitoring--auditing)
9. [Best Practices](#best-practices)

---

## System Overview

### Technology Stack
- **Payment Gateway**: CCAvenue
- **Backend**: Spring Boot 3.x with Java 17+
- **Database**: MySQL/PostgreSQL with JPA/Hibernate
- **Security**: AES-128-CBC encryption for payment data
- **Async Processing**: Spring Events + Scheduled Tasks
- **Locking**: ShedLock for distributed task coordination

### Key Features
- ✅ Dual payment flows (Product & Farmer)
- ✅ Idempotency guarantees
- ✅ Asynchronous callback processing with retry mechanism
- ✅ Comprehensive fraud detection
- ✅ Complete audit trail
- ✅ Optimistic locking for concurrency control
- ✅ Input sanitization and validation
- ✅ Rate limiting and abuse prevention

---

## Architecture Components

### 1. Core Payment Services

#### **CcAvenuePaymentService**
```
Location: src/main/java/com/spring/jwt/Payment/CcAvenuePaymentService.java
```

**Responsibilities:**
- Generate encrypted payment forms for CCAvenue
- Validate configuration on startup
- Sanitize all input parameters
- Format amounts to 2 decimal places

**Key Methods:**
- `generatePaymentForm(CcAvenuePaymentRequest)` - Creates HTML form with encrypted payment data
- `validateConfig()` - Validates merchant credentials and URLs

**Input Sanitization:**
- Order ID: Alphanumeric, hyphens, underscores, dots only
- Phone: Digits, plus, hyphens, spaces only
- Email: Standard email characters
- Text fields: Removes `&`, `=`, `#`, newlines, tabs

#### **CcAvenueUtil**
```
Location: src/main/java/com/spring/jwt/Payment/CcAvenueUtil.java
```

**Responsibilities:**
- AES-128-CBC encryption/decryption
- MD5 hashing of working key
- Hex encoding/decoding

**Encryption Details:**
- Algorithm: AES/CBC/PKCS5Padding
- IV: Fixed 16-byte array (CCAvenue standard)
- Key: MD5 hash of working key

---

### 2. Callback Processing Architecture

#### **Event-Driven + Scheduled Fallback Pattern**

```
┌─────────────────────────────────────────────────────────────┐
│                    Payment Callback Flow                     │
└─────────────────────────────────────────────────────────────┘

1. CCAvenue POST → PaymentCallbackController
                    ↓
2. Enqueue to PaymentCallbackQueue (status: PENDING)
                    ↓
3. Publish PaymentCallbackEvent
                    ↓
4. @TransactionalEventListener (AFTER_COMMIT)
                    ↓
5. PaymentCallbackProcessingService.processByQueueId()
   - Claim row (PENDING → PROCESSING)
   - Decrypt & validate
   - Business logic (confirm payment)
   - Mark DONE or RETRY/DEAD
                    ↓
6. Fallback: @Scheduled poller every 30s
   - Processes RETRY items
   - Releases stale PROCESSING rows
```

#### **PaymentCallbackController**
```
Location: src/main/java/com/spring/jwt/Payment/PaymentCallbackController.java
```

**Endpoints:**
- `POST /api/payment/product/response` - Product payment success callback
- `POST /api/payment/product/cancel` - Product payment cancellation
- `POST /api/payment/farmer/response` - Farmer payment success callback
- `POST /api/payment/farmer/cancel` - Farmer payment cancellation
- `GET /api/payment/queue/{queueId}` - Check callback processing status

**Flow:**
1. Receive encrypted response from CCAvenue
2. Extract client IP (X-Forwarded-For or remote address)
3. Enqueue callback for async processing
4. Redirect user to frontend with queue ID
5. Frontend polls `/api/payment/queue/{queueId}` for status

#### **PaymentCallbackQueueService**
```
Location: src/main/java/com/spring/jwt/Payment/PaymentCallbackQueueService.java
```

**Responsibilities:**
- Enqueue callbacks with PENDING status
- Publish events for immediate processing
- Provide status lookup for frontend polling

#### **PaymentCallbackProcessingService**
```
Location: src/main/java/com/spring/jwt/Payment/PaymentCallbackProcessingService.java
```

**Responsibilities:**
- Process callbacks in isolated transactions (`REQUIRES_NEW`)
- Decrypt CCAvenue response
- Validate order ID and amount
- Call business service to confirm payment
- Handle retries (max 5 attempts)
- Move to DEAD letter queue after max retries

**Retry Strategy:**
- Attempt 1: Immediate
- Attempt 2: +1 minute
- Attempt 3: +2 minutes
- Attempt 4: +3 minutes
- Attempt 5: +4 minutes
- After 5 failures: DEAD status

**Stale Processing Recovery:**
- Rows stuck in PROCESSING for >10 minutes are released back to RETRY
- Handles JVM crashes or network failures

#### **PaymentCallbackQueueProcessor**
```
Location: src/main/java/com/spring/jwt/Payment/PaymentCallbackQueueProcessor.java
```

**Responsibilities:**
- Event-driven processing via `@TransactionalEventListener`
- Scheduled fallback polling every 30 seconds
- Uses ShedLock to prevent duplicate processing in clustered environments

---

### 3. Business Services

#### **ProductBuyServiceImpl**
```
Location: src/main/java/com/spring/jwt/ProductBuyPending/ProductBuyServiceImpl.java
```

**Payment Flow:**
1. `createPendingOrder()` - Create pending order with PENDING status
2. Generate CCAvenue payment form
3. User completes payment on CCAvenue
4. `confirmPayment()` - Verify and confirm payment
5. Create ProductBuyConfirmed record

**Key Features:**
- User ID binding (prevents spoofing)
- Amount verification (expected vs callback)
- Order ID validation
- Optimistic locking for concurrent callbacks
- Idempotent payment confirmation

#### **FarmerPaymentServiceImpl**
```
Location: src/main/java/com/spring/jwt/FarmerPayment/FarmerPaymentServiceImpl.java
```

**Payment Flow:**
1. `initiatePayment()` - Create payment with idempotency key
2. Fraud checks (rate limiting)
3. Generate CCAvenue payment form
4. `handleCallback()` - Process payment result
5. Update payment status

**Fraud Prevention:**
- Max 5 attempts per survey in 60 minutes
- Max 20 payments per user per day
- Amount mismatch detection
- Duplicate callback detection

**Idempotency:**
- Unique key: `FARM-{surveyId}-{userId}-{uuid}`
- Returns existing payment if key already exists
- Prevents duplicate charges

---

## Payment Flows

### Product Purchase Flow

```
┌──────────┐     ┌──────────────┐     ┌──────────┐     ┌─────────────┐
│ Customer │────▶│ Create Order │────▶│ CCAvenue │────▶│   Callback  │
└──────────┘     └──────────────┘     └──────────┘     └─────────────┘
                        │                                      │
                        ▼                                      ▼
                 ProductBuyPending                    ProductBuyConfirmed
                 (status: PENDING)                    (status: SUCCESS)
```

**Step-by-Step:**

1. **Order Creation** (`POST /api/v1/product/order`)
   - Validate product exists and is active
   - Calculate total amount (price - discount) × quantity
   - Create `ProductBuyPending` record
   - Generate order ID: `PROD-{pendingId}`
   - Return encrypted payment form HTML

2. **Payment Gateway**
   - Frontend auto-submits form to CCAvenue
   - User completes payment
   - CCAvenue redirects to callback URL

3. **Callback Processing**
   - Enqueue callback
   - Decrypt response
   - Validate order ID and amount
   - Confirm payment (create `ProductBuyConfirmed`)
   - Update pending status to SUCCESS

4. **Frontend Polling**
   - Poll `/api/payment/queue/{queueId}`
   - Display success/failure message

### Farmer Registration Payment Flow

```
┌─────────┐     ┌──────────────┐     ┌──────────┐     ┌──────────┐
│ Farmer  │────▶│ Initiate Pay │────▶│ CCAvenue │────▶│ Callback │
└─────────┘     └──────────────┘     └──────────┘     └──────────┘
                       │                                     │
                       ▼                                     ▼
                FarmerPayment                        FarmerPayment
                (status: PENDING)                    (status: SUCCESS)
```

**Step-by-Step:**

1. **Payment Initiation** (`POST /api/v1/farmer-payment/initiate`)
   - Validate survey exists
   - Check if already paid
   - Fraud checks (rate limiting)
   - Create `FarmerPayment` with idempotency key
   - Generate order ID: `FARM-{surveyId}-{timestamp}`
   - Return encrypted payment form HTML

2. **Payment Gateway**
   - Frontend auto-submits form
   - User completes payment
   - CCAvenue redirects to callback URL

3. **Callback Processing**
   - Enqueue callback
   - Decrypt response
   - Validate amount
   - Update payment status
   - Audit logging

4. **Idempotency Handling**
   - If same idempotency key used again:
     - Return existing payment if SUCCESS
     - Regenerate form if PENDING
     - Create new payment if FAILED

---

## Security Architecture

### 1. Encryption

**CCAvenue Communication:**
- All payment data encrypted with AES-128-CBC
- Working key hashed with MD5
- Fixed IV as per CCAvenue specification
- Encrypted data sent as hex string

### 2. Input Sanitization

**PaymentInputSanitizer** removes dangerous characters:
- Names: `<>\"'&;(){}[]`
- Phones: Non-numeric except `+-()`
- Addresses: `<>\"'&;(){}[]`
- Order IDs: Non-alphanumeric except `_-`
- Amounts: Validated positive, max limit enforced

### 3. Authentication & Authorization

**Product Payments:**
- User must be authenticated
- Order bound to authenticated user ID
- Prevents user spoofing

**Farmer Payments:**
- User must be authenticated
- Survey ownership validated
- Admin can access all payments

### 4. Fraud Prevention

**Rate Limiting:**
- Max 5 payment attempts per survey in 60 minutes
- Max 20 payments per user per day
- Configurable via properties

**Amount Verification:**
- Callback amount must match expected amount
- Mismatch flagged as fraud
- Logged to audit trail

**Duplicate Detection:**
- Optimistic locking prevents race conditions
- Idempotency keys prevent duplicate charges
- Duplicate callbacks handled gracefully

### 5. Audit Trail

**PaymentAuditService** logs all events:
- Payment initiation
- Form generation
- Callback received
- Status changes
- Fraud attempts
- Errors and retries

**Audit Fields:**
- Payment type (PRODUCT/FARMER)
- Payment ID
- Order ID
- Action type
- Old/new status
- User ID
- IP address
- Timestamp
- Details

---

## Edge Cases & Challenges

### 1. **Concurrent Callback Handling**

**Challenge:** CCAvenue may send duplicate callbacks or user may refresh callback URL.

**Solution:**
- Optimistic locking (`@Version` field)
- Check payment status before processing
- If already SUCCESS, return existing confirmation
- Idempotent payment confirmation

**Code:**
```java
if (PaymentStatus.SUCCESS.equals(pending.getPaymentStatus())) {
    return loadConfirmedAfterDuplicateCallback(dto, pending);
}
```

### 2. **Amount Tampering**

**Challenge:** Malicious user modifies callback amount.

**Solution:**
- Store expected amount in database
- Compare callback amount with expected
- Reject if mismatch
- Log as fraud attempt

**Code:**
```java
BigDecimal expectedAmount = BigDecimal.valueOf(pending.getTotalAmount())
    .setScale(2, RoundingMode.HALF_UP);
if (expectedAmount.compareTo(callbackAmount) != 0) {
    throw new IllegalArgumentException("Amount mismatch");
}
```

### 3. **Network Failures During Callback**

**Challenge:** Callback processing fails due to network/database issues.

**Solution:**
- Queue-based processing with retry
- Max 5 retry attempts with exponential backoff
- Dead letter queue for manual intervention
- Stale processing recovery

**Retry Schedule:**
- Attempt 1: Immediate
- Attempt 2: +1 min
- Attempt 3: +2 min
- Attempt 4: +3 min
- Attempt 5: +4 min

### 4. **User Abandons Payment**

**Challenge:** User closes browser before completing payment.

**Solution:**
- Payment remains in PENDING status
- No automatic expiry (business decision)
- Admin can manually mark as FAILED
- User can retry with new payment

### 5. **CCAvenue Downtime**

**Challenge:** CCAvenue service unavailable.

**Solution:**
- Payment form generation fails gracefully
- Error message shown to user
- No pending order created
- User can retry later

### 6. **Duplicate Idempotency Keys**

**Challenge:** Same idempotency key used multiple times.

**Solution:**
- Check existing payment by idempotency key
- If SUCCESS: Return existing payment
- If PENDING: Regenerate payment form
- If FAILED: Allow new payment attempt

### 7. **Order ID Mismatch**

**Challenge:** Callback order ID doesn't match pending order.

**Solution:**
- Validate order ID before processing
- Reject if mismatch
- Log as potential fraud
- Audit trail for investigation

### 8. **JVM Crash During Processing**

**Challenge:** Server crashes while processing callback.

**Solution:**
- Callback remains in PROCESSING status
- Scheduled task releases stale PROCESSING rows (>10 min)
- Moved back to RETRY status
- Reprocessed by next poll

### 9. **Database Deadlocks**

**Challenge:** Multiple threads updating same payment record.

**Solution:**
- Optimistic locking with `@Version`
- Catch `ObjectOptimisticLockingFailureException`
- Retry with fresh data
- Idempotent handling prevents duplicate confirmation

### 10. **Callback Replay Attacks**

**Challenge:** Attacker replays old callback to trigger duplicate payment.

**Solution:**
- Check payment status (already SUCCESS)
- Validate amount and order ID
- Audit logging with IP address
- Idempotency prevents duplicate charges

### 11. **Missing Callback**

**Challenge:** CCAvenue doesn't send callback (rare).

**Solution:**
- User sees "processing" status
- Admin can manually verify with CCAvenue
- Status API integration (future enhancement)
- Manual payment confirmation option

### 12. **Partial Refunds**

**Challenge:** Customer requests refund after payment.

**Solution:**
- Not handled automatically
- Admin initiates refund via CCAvenue dashboard
- Manual status update in database
- Audit trail for refund

---

## Database Schema

### PaymentCallbackQueue

```sql
CREATE TABLE payment_callback_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_type VARCHAR(20) NOT NULL,      -- PRODUCT, FARMER
    callback_type VARCHAR(20) NOT NULL,     -- PRODUCT_RESPONSE, FARMER_CANCEL, etc.
    enc_resp TEXT,                          -- Encrypted CCAvenue response
    client_ip VARCHAR(45),                  -- IPv4/IPv6
    status VARCHAR(20) NOT NULL,            -- PENDING, PROCESSING, DONE, RETRY, DEAD
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error TEXT,
    order_id VARCHAR(120),
    result_status VARCHAR(30),              -- SUCCESS, FAILED, CANCELLED
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    version BIGINT,                         -- Optimistic locking
    INDEX idx_pay_cbq_status_next (status, next_attempt_at),
    INDEX idx_pay_cbq_created (created_at)
);
```

### ProductBuyPending

```sql
CREATE TABLE product_buy_pending (
    product_buy_pending_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount DOUBLE NOT NULL,
    payment_status VARCHAR(20) NOT NULL,    -- PENDING, SUCCESS, FAILED
    payment_gateway_order_id VARCHAR(120),
    customer_name VARCHAR(255),
    contact_number VARCHAR(20),
    delivery_address TEXT,
    created_at DATETIME NOT NULL,
    version BIGINT                          -- Optimistic locking
);
```

### ProductBuyConfirmed

```sql
CREATE TABLE product_buy_confirmed (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount DOUBLE NOT NULL,
    payment_id VARCHAR(120),                -- CCAvenue tracking ID
    payment_mode VARCHAR(20),               -- CARD, UPI, NETBANKING, WALLET
    payment_date DATETIME NOT NULL,
    customer_name VARCHAR(255),
    contact_number VARCHAR(20),
    delivery_address TEXT,
    delivery_created BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL
);
```

### FarmerPayment

```sql
CREATE TABLE farmer_payment (
    payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_public_id VARCHAR(50) UNIQUE,   -- pay_xxxxx (external ID)
    survey_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,    -- PENDING, SUCCESS, FAILED
    ccavenue_order_id VARCHAR(120) UNIQUE,
    tracking_id VARCHAR(120),               -- CCAvenue tracking ID
    bank_ref_no VARCHAR(120),
    ccavenue_payment_mode VARCHAR(50),
    status_message VARCHAR(500),
    idempotency_key VARCHAR(255) UNIQUE,
    initiator_ip VARCHAR(45),
    user_agent VARCHAR(500),
    attempt_count INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    version BIGINT                          -- Optimistic locking
);
```

### PaymentAuditLog

```sql
CREATE TABLE payment_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_type VARCHAR(20) NOT NULL,      -- PRODUCT, FARMER
    payment_id BIGINT,
    ccavenue_order_id VARCHAR(120),
    action_type VARCHAR(50) NOT NULL,       -- INITIATED, VERIFIED_SUCCESS, FRAUD_FLAGGED, etc.
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    action_by_user_id BIGINT,
    ip_address VARCHAR(45),
    details VARCHAR(2000),
    action_at DATETIME NOT NULL,
    INDEX idx_pay_audit_type_id (payment_type, payment_id),
    INDEX idx_pay_audit_action_at (action_at)
);
```

---

## Configuration

### Application Properties

```properties
# CCAvenue Configuration
ccavenue.working-key=32_character_hex_key
ccavenue.access-code=your_access_code
ccavenue.merchant-id=your_merchant_id
ccavenue.payment-url=https://secure.ccavenue.com/transaction/transaction.do?command=initiateTransaction
ccavenue.redirect-url=https://yourdomain.com/api/payment/product/response
ccavenue.cancel-url=https://yourdomain.com/api/payment/product/cancel
ccavenue.status-api-url=https://api.ccavenue.com/apis/servlet/DoWebTrans

# Farmer Payment Configuration
farmer.registration.fee=500.00

# Fraud Prevention
payment.fraud.max-attempts-per-survey=5
payment.fraud.max-attempts-window-minutes=60
payment.fraud.max-daily-payments-per-user=20

# Callback Queue Configuration
payment.callback.queue.batch-size=20
payment.callback.queue.poll-delay-ms=30000
payment.callback.queue.stale-processing-minutes=10

# Frontend URL
app.url.frontend=https://jiojigreenindia.org
```

### Security Configuration

```java
// AppConfig.java
.requestMatchers("/api/payment/queue/**").permitAll()
.requestMatchers("/api/payment/product/**").permitAll()
.requestMatchers("/api/payment/farmer/**").permitAll()
.requestMatchers("/api/v1/farmer-payment/**").authenticated()
.requestMatchers("/api/customer/orders/**").authenticated()
```

### Async Executor Configuration

```java
@Bean(name = "paymentCallbackExecutor")
public Executor paymentCallbackExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("payment-callback-");
    executor.initialize();
    return executor;
}
```

---

## Monitoring & Auditing

### Key Metrics to Monitor

1. **Callback Processing**
   - Queue depth (PENDING + RETRY count)
   - Processing time per callback
   - Retry rate
   - Dead letter queue size

2. **Payment Success Rate**
   - Total payments initiated
   - Successful payments
   - Failed payments
   - Cancelled payments

3. **Fraud Detection**
   - Rate limit violations
   - Amount mismatches
   - Duplicate callbacks
   - Unknown order IDs

4. **Performance**
   - Average payment confirmation time
   - Database query performance
   - CCAvenue response time

### Audit Queries

**Recent Failed Payments:**
```sql
SELECT * FROM payment_audit_log
WHERE action_type = 'VERIFIED_FAILED'
AND action_at > NOW() - INTERVAL 24 HOUR
ORDER BY action_at DESC;
```

**Fraud Attempts:**
```sql
SELECT * FROM payment_audit_log
WHERE action_type = 'FRAUD_FLAGGED'
ORDER BY action_at DESC
LIMIT 100;
```

**Stuck Callbacks:**
```sql
SELECT * FROM payment_callback_queue
WHERE status IN ('PENDING', 'RETRY')
AND created_at < NOW() - INTERVAL 1 HOUR;
```

**Dead Letter Queue:**
```sql
SELECT * FROM payment_callback_queue
WHERE status = 'DEAD'
ORDER BY created_at DESC;
```

---

## Best Practices

### 1. **Always Use Idempotency Keys**
- Generate unique keys for each payment attempt
- Store in database before calling payment gateway
- Check existing payment before creating new one

### 2. **Validate All Callback Data**
- Decrypt and parse carefully
- Validate order ID, amount, status
- Never trust callback data blindly

### 3. **Use Optimistic Locking**
- Add `@Version` field to payment entities
- Handle `ObjectOptimisticLockingFailureException`
- Retry with fresh data

### 4. **Implement Comprehensive Logging**
- Log all payment state transitions
- Include IP address and user agent
- Use structured logging for easy querying

### 5. **Monitor Queue Health**
- Alert on high RETRY count
- Alert on DEAD letter queue growth
- Monitor processing time

### 6. **Test Edge Cases**
- Duplicate callbacks
- Amount tampering
- Network failures
- Concurrent requests
- JVM crashes

### 7. **Secure Configuration**
- Store working key in environment variables
- Never commit credentials to git
- Rotate keys periodically
- Use HTTPS for all callbacks

### 8. **Handle Timeouts Gracefully**
- Set reasonable timeouts for CCAvenue calls
- Implement circuit breaker pattern
- Provide user-friendly error messages

### 9. **Implement Manual Intervention**
- Admin dashboard for stuck payments
- Manual payment confirmation option
- Refund workflow

### 10. **Regular Audits**
- Review fraud logs weekly
- Analyze failed payment patterns
- Monitor success rates by payment mode

---

## API Endpoints Summary

### Product Payments
- `POST /api/v1/product/order` - Create order and initiate payment
- `POST /api/payment/product/response` - CCAvenue success callback
- `POST /api/payment/product/cancel` - CCAvenue cancel callback
- `GET /api/payment/queue/{queueId}` - Check callback status

### Farmer Payments
- `POST /api/v1/farmer-payment/initiate` - Initiate farmer registration payment
- `POST /api/payment/farmer/response` - CCAvenue success callback
- `POST /api/payment/farmer/cancel` - CCAvenue cancel callback
- `GET /api/v1/farmer-payment/{paymentId}` - Get payment details
- `GET /api/v1/farmer-payment/survey/{surveyId}` - Get payments by survey
- `GET /api/v1/farmer-payment/order/{orderId}` - Get payment by order ID
- `POST /api/v1/farmer-payment/bulk-status` - Get payment status for multiple surveys

---

## Conclusion

This payment gateway integration provides a robust, secure, and scalable solution for handling online payments. The architecture addresses common challenges like concurrency, idempotency, fraud prevention, and failure recovery through well-designed patterns and comprehensive error handling.

Key strengths:
- **Reliability**: Queue-based processing with retry mechanism
- **Security**: Encryption, input sanitization, fraud detection
- **Scalability**: Async processing, optimistic locking, distributed task coordination
- **Observability**: Comprehensive audit trail and monitoring
- **Maintainability**: Clean separation of concerns, well-documented code

For production deployment, ensure proper monitoring, alerting, and regular security audits are in place.

---

**Document Version**: 1.0  
**Last Updated**: April 11, 2026  
**Author**: System Architecture Team
