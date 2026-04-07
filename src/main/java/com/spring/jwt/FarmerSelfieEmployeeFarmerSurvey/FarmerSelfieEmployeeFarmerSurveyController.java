package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import com.spring.jwt.EmployeeFarmerSurvey.BaseResponseDTO1;
import com.spring.jwt.Enums.PhotoType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller responsible for handling Farmer Selfie operations
 * related to Employee Farmer Survey.
 *
 * This controller supports:
 *  - Uploading farmer selfie images
 *  - Fetching selfie details by selfie ID
 *  - Fetching selfie details by survey ID
 *  - Updating (patching) the selfie image only
 *
 * All responses are wrapped using BaseResponseDTO1 for consistency
 * across the application.
 */
@RestController
@RequestMapping("/api/v1/farmer_selfie_Survey")
@RequiredArgsConstructor
public class FarmerSelfieEmployeeFarmerSurveyController {

    /**
     * Service layer dependency handling all business logic
     * related to farmer selfie operations.
     */
    private final FarmerSelfieEmployeeFarmerSurveyService selfieService;


    /**
     * Upload a farmer selfie for a specific survey and photo type.
     *
     * This API:
     * - Accepts multipart image upload
     * - Associates selfie with a survey
     * - Differentiates selfie using PhotoType (FRONT / LEFT / RIGHT etc.)
     * - Prevents duplicate uploads for same survey + photoType
     *
     * HTTP Method: POST
     * URL: /api/v1/farmer-selfie/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseUploadDTO>> uploadSelfie(

            @RequestParam Long surveyId,
            @RequestParam PhotoType photoType,
            @RequestParam MultipartFile file) {

        // Delegate business logic to service layer
        FarmerSelfieResponseUploadDTO response =
                selfieService.uploadSelfie(surveyId, photoType, file);

        // Return CREATED (201) status with standardized response wrapper
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BaseResponseDTO1<>(
                        "201",
                        "Farmer selfie uploaded successfully",
                        response
                ));
    }




    /**
     * Fetch farmer selfie details using selfieId.
     *
     * Use case:
     * - Admin or mobile app wants selfie details using unique selfie identifier
     *
     * HTTP Method: GET
     * URL: /api/v1/farmer-selfie/{selfieId}
     */
    @GetMapping("/{selfieId}")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseDTO>> getBySelfieId(

            // Unique ID of the selfie
            @PathVariable Long selfieId) {

        // Fetch selfie details from service
        FarmerSelfieResponseDTO response =
                selfieService.getSelfieById(selfieId);

        // Return OK (200) with response data
        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Farmer selfie fetched successfully",
                        response
                )
        );
    }



    /**
     * Fetch farmer selfie using selfieId and photoType.
     *
     * Use case:
     * - Same selfieId may contain multiple photo types
     * - Client explicitly wants a specific photoType
     *
     * HTTP Method: GET
     * URL: /api/v1/farmer-selfie/{selfieId}/photo-type/{photoType}
     */
    @GetMapping("/{selfieId}/photo-type/{photoType}")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseDTO>> getBySelfieIdAndPhotoType(

            // Selfie primary identifier
            @PathVariable Long selfieId,

            // Enum path variable (validated automatically by Spring)
            @PathVariable PhotoType photoType) {

        // Service fetch with composite condition
        FarmerSelfieResponseDTO response =
                selfieService.getSelfieByIdAndPhotoType(selfieId, photoType);

        // Return successful response
        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Farmer selfie fetched successfully by selfieId and photoType",
                        response
                )
        );
    }


    /**
     * Fetch farmer selfie using surveyId.
     *
     * Use case:
     * - While viewing survey details, display associated selfie
     *
     * HTTP Method: GET
     * URL: /api/v1/farmer-selfie/survey/{surveyId}
     */
    @GetMapping("/survey/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseDTO>> getBySurveyId(

            // Survey identifier
            @PathVariable Long surveyId) {

        // Retrieve selfie associated with the survey
        FarmerSelfieResponseDTO response =
                selfieService.getSelfieBySurveyId(surveyId);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Farmer selfie fetched successfully by surveyId",
                        response
                )
        );
    }



    /**
     * Fetch farmer selfie using surveyId and photoType.
     *
     * Use case:
     * - Survey may have multiple selfies
     * - Client needs a specific selfie angle/type
     *
     * HTTP Method: GET
     * URL: /api/v1/farmer-selfie/survey/{surveyId}/photo-type/{photoType}
     */
    @GetMapping("/survey/{surveyId}/photo-type/{photoType}")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseDTO>> getBySurveyIdAndPhotoType(

            // Survey identifier
            @PathVariable Long surveyId,

            // Enum representing selfie type
            @PathVariable PhotoType photoType) {

        // Fetch selfie based on survey + photoType
        FarmerSelfieResponseDTO response =
                selfieService.getSelfieBySurveyIdAndPhotoType(surveyId, photoType);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Farmer selfie fetched successfully by surveyId and photoType",
                        response
                )
        );
    }


    /**
     * Update (replace) selfie image.
     *
     * Notes:
     * - Only image is updated
     * - photoType and survey mapping remain unchanged
     * - PATCH is used as this is a partial update
     *
     * HTTP Method: PATCH
     * URL: /api/v1/farmer-selfie/{selfieId}
     */
    @PatchMapping("/{selfieId}")
    public ResponseEntity<BaseResponseDTO1<FarmerSelfieResponseDTO>> updateSelfieImage(

            // Selfie identifier whose image is to be replaced
            @PathVariable Long selfieId,

            // New image file
            @RequestParam MultipartFile image) {

        // Perform image replacement
        FarmerSelfieResponseDTO response =
                selfieService.updateSelfieImage(selfieId, image);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Farmer selfie image updated successfully",
                        response
                )
        );
    }

}
