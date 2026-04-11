package com.spring.jwt.Payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ccavenue")
public class CcAvenueConfig {

    private String workingKey;
    private String accessCode;
    private String merchantId;
    private String statusApiUrl;
    private String redirectUrl;
    private String cancelUrl;
    private String paymentUrl;
}
