package com.spring.jwt.FarmerLabReport;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FarmerLabReportViewDTO {
    private Long reportId;
    private Long surveyId;
    private LocalDateTime uploadedAt;
    private String downloadUrl;
}
