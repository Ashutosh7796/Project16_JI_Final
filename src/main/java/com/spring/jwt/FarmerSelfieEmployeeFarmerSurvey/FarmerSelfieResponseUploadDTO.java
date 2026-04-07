package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import com.spring.jwt.Enums.PhotoType;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class FarmerSelfieResponseUploadDTO {
    private Long selfieId;
    private Long surveyId;
    private PhotoType photoType;
    private LocalDateTime takenAt;
}
