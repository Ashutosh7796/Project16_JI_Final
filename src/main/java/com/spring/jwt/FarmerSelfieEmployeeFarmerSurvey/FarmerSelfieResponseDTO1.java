package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class FarmerSelfieResponseDTO1 {
    private String selfieImageUrl;
    private String signatureImageUrl;
    private LocalDateTime selfieTakenAt;
    private String message;
}
