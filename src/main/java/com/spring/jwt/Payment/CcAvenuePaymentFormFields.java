package com.spring.jwt.Payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Structured representation of the CCAvenue auto-submit form. Frontend builds the {@code <form>}
 * with DOM APIs (no {@code innerHTML}) — eliminates the XSS surface that the legacy server-side
 * HTML-string approach has.
 */
@Data
@Builder
@AllArgsConstructor
public class CcAvenuePaymentFormFields {
    /** Absolute URL the form must POST to (e.g. {@code https://secure.ccavenue.com/transaction/transaction.do?...}). */
    private String actionUrl;
    /** Hidden form fields to render (key = input name, value = input value). */
    private Map<String, String> fields;
}
