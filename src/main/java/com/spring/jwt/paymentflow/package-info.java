/**
 * <h1>Payment flow engine (Amazon-style semantics)</h1>
 *
 * <h2>State machine (strict)</h2>
 * <pre>
 *   INITIATED ──create──► (client) ──initiate──► PROCESSING (gateway payload ready / user redirect)
 *        │                      │                        │
 *        │                      │                        ├──► SUCCESS (webhook verified)
 *        │                      │                        ├──► FAILED (deterministic error or timeout/reconcile)
 *        │                      │                        └──► PENDING_GATEWAY (uncertain gateway response)
 *        │                      │                                     │
 *        │                      │                                     ├──► SUCCESS / FAILED (webhook or job)
 *        │                      └──► ABANDONED (beacon / explicit) ───┘
 *        └──► FAILED (validation)
 * </pre>
 *
 * <p><b>Why {@code /api/v1/payment-engine}</b> — avoids collision with legacy {@code /api/payment/**} routes in
 * {@code AppConfig} while staying under authenticated user API.</p>
 *
 * <p><b>Final truth</b> — webhooks + reconciliation; never trust the browser for SUCCESS.</p>
 */
package com.spring.jwt.paymentflow;
