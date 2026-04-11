package com.spring.jwt.FarmerLabReport;

import com.spring.jwt.exception.DocumentAlreadyExistsException;
import com.spring.jwt.exception.ResourceNotFoundException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for managing Farmer Lab Reports.
 *
 * <p>
 * This service handles the complete lifecycle of a farmer's laboratory report
 * associated with an {@code EmployeeFarmerSurvey}. It supports secure upload,
 * retrieval, update, deletion, and download of lab report PDF documents.
 * </p>
 *
 * <p>
 * Business Rules:
 * <ul>
 *   <li>Only one lab report is allowed per survey.</li>
 *   <li>Lab reports are uniquely identified by {@code surveyId}.</li>
 *   <li>Only PDF files are allowed for upload and update.</li>
 *   <li>Duplicate uploads for the same survey are not permitted.</li>
 * </ul>
 * </p>
 *
 * <p>
 * All operations must validate survey existence and handle domain-specific
 * exceptions such as {@code ResourceNotFoundException} and
 * {@code DocumentAlreadyExistsException}.
 * </p>
 *
 * @author
 *   Backend Team
 * @since 1.0
 */
public interface FarmerLabReportService {

    /**
     * Uploads a new lab report PDF for a given survey.
     *
     * <p>
     * This operation creates a new lab report entry and associates it with
     * the provided {@code surveyId}. If a lab report already exists for the
     * survey, the operation will fail.
     * </p>
     *
     * @param surveyId the unique identifier of the survey
     * @param file the PDF file containing the lab report
     * @return {@link FarmerLabReportUploadDTO} containing uploaded report details
     *
     * @throws IllegalArgumentException if {@code surveyId} is invalid or file is empty/invalid
     * @throws ResourceNotFoundException if the survey does not exist
     * @throws DocumentAlreadyExistsException if a lab report already exists for the survey
     */
    FarmerLabReportUploadDTO uploadLabReport(Long surveyId, MultipartFile file);

    /**
     * Retrieves lab report details using the associated survey ID.
     *
     * <p>
     * This method returns metadata and access information for the lab report
     * without triggering a file download.
     * </p>
     *
     * @param surveyId the unique identifier of the survey
     * @return {@link FarmerLabReportViewDTO} containing lab report information
     *
     * @throws ResourceNotFoundException if no lab report exists for the given survey
     */
    FarmerLabReportViewDTO viewLabReport(Long surveyId);

    /**
     * Retrieves lab report details by the lab report ID.
     *
     * <p>
     * This method is typically used for administrative or internal use cases
     * where the report ID is available.
     * </p>
     *
     * @param reportId the unique identifier of the lab report
     * @return {@link FarmerLabReportViewDTO} containing lab report information
     *
     * @throws ResourceNotFoundException if no lab report exists with the given ID
     */
    FarmerLabReportViewDTO getLabReportById(Long reportId);

    /**
     * Updates (patches) an existing lab report PDF using the survey ID.
     *
     * <p>
     * This operation replaces the previously uploaded lab report file with
     * a new PDF while keeping the same survey association.
     * </p>
     *
     * @param surveyId the unique identifier of the survey
     * @param file the new PDF file to replace the existing lab report
     * @return {@link FarmerLabReportUploadDTO} containing updated report details
     *
     * @throws IllegalArgumentException if file is invalid or empty
     * @throws ResourceNotFoundException if no lab report exists for the survey
     */
    FarmerLabReportUploadDTO updateLabReportBySurveyId(Long surveyId, MultipartFile file);

    /**
     * Deletes the lab report associated with the given survey ID.
     *
     * <p>
     * This operation permanently removes the lab report record and its
     * associated document from the system.
     * </p>
     *
     * @param surveyId the unique identifier of the survey
     *
     * @throws ResourceNotFoundException if no lab report exists for the survey
     */
    void deleteLabReportBySurveyId(Long surveyId);

    /**
     * Downloads the lab report PDF associated with the given survey ID.
     *
     * <p>
     * This method returns the raw PDF content as a byte array, suitable for
     * file download or inline viewing in the client.
     * </p>
     *
     * @param surveyId the unique identifier of the survey
     * @return byte array representing the PDF file content
     *
     * @throws ResourceNotFoundException if no lab report exists for the survey
     */
    byte[] downloadLabReport(Long surveyId);
}
