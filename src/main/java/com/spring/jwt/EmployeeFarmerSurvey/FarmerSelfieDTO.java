package com.spring.jwt.EmployeeFarmerSurvey;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FarmerSelfieDTO {

    private String imageUrl;
    private String imageUrlS;
    private LocalDateTime takenAt;
    private String message;
}
