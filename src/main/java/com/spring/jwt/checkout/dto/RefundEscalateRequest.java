package com.spring.jwt.checkout.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundEscalateRequest {

    @Size(max = 120, message = "supportTicketId must be <= 120 chars")
    private String supportTicketId;

    @Size(max = 2000, message = "notes must be <= 2000 chars")
    private String notes;
}
