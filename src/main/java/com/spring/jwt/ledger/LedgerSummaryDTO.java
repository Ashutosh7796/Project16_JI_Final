package com.spring.jwt.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class LedgerSummaryDTO {
    private BigDecimal totalCollected;
    private BigDecimal totalRefunded;
    private BigDecimal netAmount;
    private long totalPayments;
    private long totalRefunds;
    private long failedTransactions;
}
