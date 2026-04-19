package com.spring.jwt.paymentflow;

import com.spring.jwt.paymentflow.gateway.PaymentGatewayAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentFlowGatewayRegistry {

    private final Map<String, PaymentGatewayAdapter> byKind;

    public PaymentFlowGatewayRegistry(List<PaymentGatewayAdapter> adapters) {
        this.byKind = adapters.stream().collect(Collectors.toMap(PaymentGatewayAdapter::kind, Function.identity(), (a, b) -> a));
    }

    public PaymentGatewayAdapter require(String kind) {
        PaymentGatewayAdapter a = byKind.get(kind);
        if (a == null) {
            throw new IllegalStateException("No payment gateway adapter registered for kind: " + kind);
        }
        return a;
    }
}
