package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import com.spring.jwt.EmployeeFarmerSurvey.EmployeeFarmerSurveyRepository;
import com.spring.jwt.Enums.FormStatus;
import com.spring.jwt.Enums.PhotoType;
import com.spring.jwt.entity.EmployeeFarmerSurvey;
import com.spring.jwt.entity.FarmerSelfieEmployeeFarmerSurvey;
import com.spring.jwt.exception.DocumentAlreadyExistsException;
import com.spring.jwt.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service implementation responsible for managing Farmer Selfie operations
 * associated with Employee Farmer Surveys.
 *
 * Responsibilities:
 *  - Upload farmer selfie for a survey
 *  - Fetch selfie by selfie ID or survey ID
 *  - Update (patch) selfie image only
 *
 * Design Notes:
 *  - Base64 image is stored directly in DB
 *  - One selfie per survey is enforced
 *  - Survey status is activated once selfie is uploaded
 *  - All write operations are transactional
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FarmerSelfieEmployeeFarmerSurveyServiceImpl
        implements FarmerSelfieEmployeeFarmerSurveyService {

    /**
     * Repository for Farmer Selfie persistence operations
     */
    private final FarmerSelfieEmployeeFarmerSurveyRepository selfieRepository;

    /**
     * Repository for Employee Farmer Survey persistence operations
     */
    private final EmployeeFarmerSurveyRepository surveyRepository;


    /**
     * Upload a farmer selfie image for a given survey.
     *
     * Business Rules:
     *  - Survey must exist
     *  - Only one selfie allowed per survey
     *  - Only valid image files are accepted
     *  - Survey status is updated to ACTIVE after successful upload
     *
     * @param surveyId ID of the employee farmer survey
     * @param file     Image file to upload
     * @return         FarmerSelfieResponseDTO
     */
    @Override
    @Transactional
    public FarmerSelfieResponseUploadDTO uploadSelfie(
            Long surveyId,
            PhotoType photoType,
            MultipartFile file) {

        validateSurveyId(surveyId);
        validateImage(file);

        if (photoType == null) {
            throw new IllegalArgumentException("PhotoType is required");
        }

        EmployeeFarmerSurvey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Survey not found with ID: " + surveyId));

        if (selfieRepository.existsBySurvey_SurveyIdAndPhotoType(surveyId, photoType)) {
            throw new DocumentAlreadyExistsException(
                    "Selfie already uploaded for survey ID " +
                            surveyId + " with photoType " + photoType);
        }

        FarmerSelfieEmployeeFarmerSurvey selfie = new FarmerSelfieEmployeeFarmerSurvey();
        selfie.setSurvey(survey);
        selfie.setPhotoType(photoType);
        selfie.setImageUrl(encodeBase64(file));
        selfie.setTakenAt(LocalDateTime.now());

        FarmerSelfieEmployeeFarmerSurvey saved = selfieRepository.save(selfie);

        survey.setFormStatus(FormStatus.ACTIVE);
        surveyRepository.save(survey);

        log.info("Selfie uploaded for surveyId={}, photoType={}", surveyId, photoType);

        return mapToResponseUpload(saved);
    }



    /**
     * Fetch farmer selfie details using selfie ID.
     *
     * @param selfieId Unique ID of the farmer selfie
     * @return         FarmerSelfieResponseDTO
     */
    @Override
    public FarmerSelfieResponseDTO getSelfieById(Long selfieId) {

        FarmerSelfieEmployeeFarmerSurvey selfie = selfieRepository.findById(selfieId).orElseThrow(() -> new ResourceNotFoundException("Selfie not found with ID: " + selfieId));
        return mapToResponse(selfie);
    }

    @Override
    public FarmerSelfieResponseDTO getSelfieByIdAndPhotoType(
            Long selfieId, PhotoType photoType) {

        if (photoType == null) {
            throw new IllegalArgumentException("PhotoType is required");
        }

        FarmerSelfieEmployeeFarmerSurvey selfie =
                selfieRepository
                        .findByFarmerSelfieEmployeeFarmerSurveyIdAndPhotoType(
                                selfieId, photoType)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Selfie not found with ID: " +
                                                selfieId + " and photoType: " + photoType));

        return mapToResponse(selfie);
    }



    /**
     * Fetch farmer selfie details using survey ID.
     *
     * Typical Use Case:
     *  - Display selfie while viewing survey details
     *
     * @param surveyId ID of the employee farmer survey
     * @return         FarmerSelfieResponseDTO
     */
    @Override
    public FarmerSelfieResponseDTO getSelfieBySurveyId(Long surveyId) {

      return null;
    }

    @Override
    public FarmerSelfieResponseDTO getSelfieBySurveyIdAndPhotoType(
            Long surveyId, PhotoType photoType) {

        if (photoType == null) {
            throw new IllegalArgumentException("PhotoType is required");
        }

        FarmerSelfieEmployeeFarmerSurvey selfie =
                selfieRepository
                        .findBySurvey_SurveyIdAndPhotoType(surveyId, photoType)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Selfie not found for survey ID: " +
                                                surveyId + " and photoType: " + photoType));

        return mapToResponse(selfie);
    }



    /**
     * Update (replace) the farmer selfie image.
     *
     * Notes:
     *  - Only the image is updated
     *  - Existing selfie metadata remains unchanged
     *
     * @param selfieId ID of the selfie to update
     * @param file     New image file
     * @return         Updated FarmerSelfieResponseDTO
     */
    @Override
    @Transactional
    public FarmerSelfieResponseDTO updateSelfieImage(
            Long selfieId,
            MultipartFile file) {

        validateImage(file);

        FarmerSelfieEmployeeFarmerSurvey selfie =
                selfieRepository.findById(selfieId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Selfie not found with ID: " + selfieId));

        selfie.setImageUrl(encodeBase64(file));
        selfie.setTakenAt(LocalDateTime.now());

        return mapToResponse(selfieRepository.save(selfie));
    }


    /**
     * Validate survey ID input.
     */
    private void validateSurveyId(Long surveyId) {
        if (surveyId == null) {
            throw new IllegalArgumentException("Survey ID must not be null");
        }
    }

    /**
     * Validate uploaded image file.
     *
     * Rules:
     *  - File must not be null or empty
     *  - Only image MIME types are allowed
     *  - Maximum file size: 5MB
     */
    private void validateImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getContentType() == null ||
                !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Image size must be less than 5MB");
        }
    }

    /**
     * Convert MultipartFile to Base64 encoded string.
     */
    private String encodeBase64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process image file", e);
        }
    }

    /**
     * Map entity to response DTO.
     */
    private FarmerSelfieResponseDTO mapToResponse(
            FarmerSelfieEmployeeFarmerSurvey entity) {

        FarmerSelfieResponseDTO dto = new FarmerSelfieResponseDTO();
        dto.setSelfieId(entity.getFarmerSelfieEmployeeFarmerSurveyId());
        dto.setSurveyId(entity.getSurvey().getSurveyId());
        dto.setImageUrl(entity.getImageUrl());
        dto.setTakenAt(entity.getTakenAt());
        dto.setPhotoType(entity.getPhotoType());
        return dto;
    }
    private FarmerSelfieResponseUploadDTO mapToResponseUpload(
            FarmerSelfieEmployeeFarmerSurvey entity) {

        FarmerSelfieResponseUploadDTO dto = new FarmerSelfieResponseUploadDTO();
        dto.setSelfieId(entity.getFarmerSelfieEmployeeFarmerSurveyId());
        dto.setSurveyId(entity.getSurvey().getSurveyId());
        dto.setTakenAt(entity.getTakenAt());
        dto.setPhotoType(entity.getPhotoType());
        return dto;
    }
}
