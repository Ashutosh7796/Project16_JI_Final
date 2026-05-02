package com.spring.jwt.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class LedgerEntryDTO {
    private UUID id;
    private Long orderId;
    private Long paymentId;
    private Long refundId;
    private String type;
    private BigDecimal amount;
    private String status;
    private String source;
    private String gatewayTrackingId;
    private String metadata;
    private LocalDateTime createdAt;

    public static LedgerEntryDTO from(LedgerEntry entry) {
        return LedgerEntryDTO.builder()
                .id(entry.getId())
                .orderId(entry.getOrderId())
                .paymentId(entry.getPaymentId())
                .refundId(entry.getRefundId())
                .type(entry.getType().name())
                .amount(entry.getAmount())
                .status(entry.getStatus().name())
                .source(entry.getSource().name())
                .gatewayTrackingId(entry.getGatewayTrackingId())
                .metadata(entry.getMetadata())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
