package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.Enums.FormStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing Employee–Farmer Survey operations.
 *
 * This controller exposes secure APIs for:
 *  - Creating farmer surveys
 *  - Fetching surveys (by ID, by user, logged-in user)
 *  - Updating surveys (PATCH – partial update)
 *  - Deleting surveys
 *
 * All responses follow a standard BaseResponseDTO structure
 * to maintain consistency across the application.
 *
 * Security:
 *  - Logged-in user information is derived from JWT via SecurityContext
 *  - No userId is trusted directly from the client for sensitive operations
 *
 * Pagination:
 *  - Applied using Spring Pageable
 *  - Default sorting: surveyId DESC
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/employeeFarmerSurveys")
@RequiredArgsConstructor
public class EmployeeFarmerSurveyController {

    private final EmployeeFarmerSurveyService employeeFarmerSurveyService;

    /**
     * Create a new Employee–Farmer Survey.
     *
     * - Survey is always created for the logged-in user (JWT based)
     * - Form number is generated automatically by the backend
     * - Duplicate farmer mobile numbers are validated
     *
     * @param dto Survey request payload
     * @return Created survey details with generated surveyId and formNumber
     */
    @PostMapping("/add")
    public ResponseEntity<BaseResponseDTO1<EmployeeFarmerSurveyDTO>> createSurvey(
            @Valid @RequestBody EmployeeFarmerSurveyRegDTO dto) {

        EmployeeFarmerSurveyDTO result = employeeFarmerSurveyService.createSurvey(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BaseResponseDTO1<>("201", "Survey created successfully", result));
    }

    /**
     * Fetch a survey by its unique surveyId.
     *
     * @param surveyId Unique identifier of the survey
     * @return Survey details if found
     */
    @GetMapping("/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<EmployeeFarmerSurveyDTO>> getSurveyById(
            @PathVariable Long surveyId) {

        EmployeeFarmerSurveyDTO result = employeeFarmerSurveyService.getSurveyById(surveyId);

        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Survey fetched successfully", result)
        );
    }

    /**
     * Fetch all surveys (Admin-level operation).
     *
     * - Supports pagination and sorting
     * - Default sorting: surveyId DESC
     *
     * @param pageable Pagination and sorting configuration
     * @return Paginated list of surveys
     */
    @GetMapping
    public ResponseEntity<BaseResponseDTO1<Page<EmployeeFarmerSurveyDTO>>> getAllSurveys(
            @PageableDefault(page = 0, size = 10, sort = "surveyId", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<EmployeeFarmerSurveyDTO> page = employeeFarmerSurveyService.getAllSurveys(pageable);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Surveys fetched successfully", page));
    }

    /**
     * Partially update an existing survey.
     *
     * - PATCH operation (null-safe updates)
     * - Only provided fields are updated
     * - Ownership validation is applied
     *
     * @param surveyId Survey identifier
     * @param dto      Fields to update
     * @return Updated survey details
     */
    @PatchMapping("/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<EmployeeFarmerSurveyDTO>> updateSurvey(
            @PathVariable Long surveyId,
            @RequestBody EmployeeFarmerSurveyDTO dto) {

        EmployeeFarmerSurveyDTO result = employeeFarmerSurveyService.updateSurvey(surveyId, dto);

        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Survey updated successfully", result));
    }

    /**
     * Delete a survey by its surveyId.
     *
     * - Soft or hard delete depends on service implementation
     *
     * @param surveyId Survey identifier
     * @return Success message
     */
    @DeleteMapping("/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<Void>> deleteSurvey(
            @PathVariable Long surveyId) {
        
        log.debug("Delete request received for survey ID: {}", surveyId);
        employeeFarmerSurveyService.deleteSurvey(surveyId);
        log.info("Survey deleted successfully: {}", surveyId);
        
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Survey deleted successfully", null));
    }

    /**
     * Fetch surveys for a specific user (Admin use-case).
     *
     * - Requires userId as path variable
     * - Supports pagination
     *
     * @param userId   User identifier
     * @param pageable Pagination configuration
     * @return Paginated list of surveys for the user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<BaseResponseDTO1<Page<EmployeeFarmerSurveyDTO>>> getSurveysByUserId(
            @PathVariable Long userId,
            @PageableDefault(page = 0, size = 10, sort = "surveyId", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<EmployeeFarmerSurveyDTO> page = employeeFarmerSurveyService.getByUserIdSurveys(userId, pageable);

        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "User surveys fetched successfully", page));
    }

    /**
     * Fetch surveys for the currently logged-in user.
     *
     * - User identity is derived from JWT token
     * - No userId is accepted from the client
     *
     * @param pageable Pagination configuration
     * @return Paginated list of logged-in user's surveys
     */
    @GetMapping("/my")
    public ResponseEntity<BaseResponseDTO1<Page<EmployeeFarmerSurveyDTO>>> getMySurveys(
            @PageableDefault(page = 0, size = 10, sort = "surveyId", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<EmployeeFarmerSurveyDTO> page = employeeFarmerSurveyService.getMySurveys(pageable);

        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "My surveys fetched successfully", page));
    }

    @GetMapping("/status-count/all")
    public ResponseEntity<SurveyStatusCountDTO> getAllStatusCount() {
        return ResponseEntity.ok(
                employeeFarmerSurveyService.getAllSurveyStatusCount()
        );
    }

    @GetMapping("/status-count/me")
    public ResponseEntity<SurveyStatusCountDTO> getMyStatusCount() {
        return ResponseEntity.ok(
                employeeFarmerSurveyService.getSurveyStatusCountByLoggedInUser()
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<EmployeeFarmerSurveyDTO>> getAllByStatus(
            @PathVariable FormStatus status,
            Pageable pageable) {

        return ResponseEntity.ok(
                employeeFarmerSurveyService.getAllSurveysByStatus(status, pageable)
        );
    }


    @GetMapping("/me/status/{status}")
    public ResponseEntity<Page<EmployeeFarmerSurveyDTO>> getMyByStatus(
            @PathVariable FormStatus status,
            Pageable pageable) {

        return ResponseEntity.ok(
                employeeFarmerSurveyService.getMySurveysByStatus(status, pageable)
        );
    }
}
