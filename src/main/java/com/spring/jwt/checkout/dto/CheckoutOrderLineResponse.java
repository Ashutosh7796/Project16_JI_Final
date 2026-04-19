package com.spring.jwt.checkout.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutOrderLineResponse {
    private Long lineId;
    private Long productId;
    /** Display name from current product catalog (snapshot price is on the line). */
    private String productName;
    private Integer quantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal lineTotal;
    private String fulfillmentStatus;
}
