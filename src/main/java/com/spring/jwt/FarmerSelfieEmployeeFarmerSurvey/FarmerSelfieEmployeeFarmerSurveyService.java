package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import com.spring.jwt.Enums.PhotoType;
import org.springframework.web.multipart.MultipartFile;

public interface  FarmerSelfieEmployeeFarmerSurveyService {
    FarmerSelfieResponseUploadDTO uploadSelfie(Long surveyId, PhotoType photoType, MultipartFile file);

    FarmerSelfieResponseDTO getSelfieById(Long selfieId);

    FarmerSelfieResponseDTO getSelfieByIdAndPhotoType(Long selfieId,PhotoType photoType);

    FarmerSelfieResponseDTO getSelfieBySurveyId(Long surveyId);
    FarmerSelfieResponseDTO getSelfieBySurveyIdAndPhotoType(Long surveyId,PhotoType photoType);

    FarmerSelfieResponseDTO updateSelfieImage(Long selfieId, MultipartFile file);

}
