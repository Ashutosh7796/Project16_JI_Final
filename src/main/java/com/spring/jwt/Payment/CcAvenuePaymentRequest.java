package com.spring.jwt.Payment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CcAvenuePaymentRequest {

    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String redirectUrl;
    private String cancelUrl;
    private String billingName;
    private String billingAddress;
    private String billingTel;
    private String billingEmail;
}
