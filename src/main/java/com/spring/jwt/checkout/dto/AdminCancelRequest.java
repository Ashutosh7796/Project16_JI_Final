package com.spring.jwt.checkout.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCancelRequest {
    private String reason;
}
