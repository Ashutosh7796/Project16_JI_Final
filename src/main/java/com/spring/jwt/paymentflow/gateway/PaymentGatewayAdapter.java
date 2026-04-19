package com.spring.jwt.paymentflow.gateway;

import com.spring.jwt.paymentflow.PaymentFlowEntity;

/**
 * Pluggable gateway: CCAvenue (server-side form), Razorpay order create, Stripe PaymentIntent, etc.
 */
public interface PaymentGatewayAdapter {

    String kind();

    PaymentGatewayResult initiate(PaymentFlowEntity payment);
}
