package com.spring.jwt.checkout.dto;

import lombok.Data;

@Data
public class RefundEvidenceRequest {
    private String supportTicketId;
    private String bankReference;
    private String adminNotes;
}
