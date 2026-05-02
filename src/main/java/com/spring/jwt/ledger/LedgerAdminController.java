package com.spring.jwt.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LedgerAdminController {

    private final LedgerService ledgerService;

    /**
     * Get paginated, filtered list of all ledger entries.
     */
    @GetMapping
    public Page<LedgerEntryDTO> list(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) LedgerEntryType type,
            @RequestParam(required = false) LedgerEntryStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ledgerService.findAll(orderId, type, status, startDate, endDate, pageable);
    }

    /**
     * Get all ledger entries for a specific order.
     */
    @GetMapping("/{orderId}")
    public List<LedgerEntryDTO> getByOrder(@PathVariable Long orderId) {
        return ledgerService.findByOrderId(orderId);
    }

    /**
     * Get summary metrics for the financial ledger dashboard.
     */
    @GetMapping("/summary")
    public LedgerSummaryDTO getSummary() {
        return ledgerService.getSummary();
    }
}
