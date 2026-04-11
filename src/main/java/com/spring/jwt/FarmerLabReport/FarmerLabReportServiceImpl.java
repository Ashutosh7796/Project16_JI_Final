package com.spring.jwt.FarmerLabReport;

import com.spring.jwt.EmployeeFarmerSurvey.EmployeeFarmerSurveyRepository;
import com.spring.jwt.entity.EmployeeFarmerSurvey;
import com.spring.jwt.entity.FarmerLabReport;
import com.spring.jwt.exception.DocumentAlreadyExistsException;
import com.spring.jwt.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmerLabReportServiceImpl implements FarmerLabReportService {

    private final FarmerLabReportRepository labReportRepository;
    private final EmployeeFarmerSurveyRepository surveyRepository;

    /* ===================== UPLOAD ===================== */

    @Override
    @Transactional
    public FarmerLabReportUploadDTO uploadLabReport(Long surveyId, MultipartFile file) {

        validateSurveyId(surveyId);
        validatePdf(file);

        EmployeeFarmerSurvey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Survey not found with ID: " + surveyId));

        if (labReportRepository.existsBySurvey_SurveyId(surveyId)) {
            throw new DocumentAlreadyExistsException(
                    "Lab report already uploaded for survey ID: " + surveyId);
        }

        try {
            FarmerLabReport report = new FarmerLabReport();
            report.setSurvey(survey);

            // ✅ SAVE DIRECTLY AS BLOB
            report.setPdfUrl(file.getBytes());

            report.setUploadedAt(LocalDateTime.now());

            FarmerLabReport saved = labReportRepository.save(report);

            log.info("Lab report uploaded successfully for surveyId={}", surveyId);

            return mapToUploadDTO(saved);

        } catch (Exception e) {
            log.error("Failed to upload lab report for survey {}", surveyId, e);
            throw new RuntimeException("Failed to upload lab report", e);
        }
    }

    /* ===================== VIEW ===================== */

    @Override
    public FarmerLabReportViewDTO viewLabReport(Long surveyId) {

        FarmerLabReport report = labReportRepository.findBySurvey_SurveyId(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Lab report not found for survey ID: " + surveyId));

        return mapToViewDTO(report);
    }

    @Override
    public FarmerLabReportViewDTO getLabReportById(Long reportId) {

        FarmerLabReport report = labReportRepository.findById(reportId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Lab report not found with ID: " + reportId));

        return mapToViewDTO(report);
    }

    /* ===================== UPDATE ===================== */

    @Override
    @Transactional
    public FarmerLabReportUploadDTO updateLabReportBySurveyId(Long surveyId, MultipartFile file) {

        validateSurveyId(surveyId);
        validatePdf(file);

        FarmerLabReport report = labReportRepository.findBySurvey_SurveyId(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Lab report not found for survey ID: " + surveyId));

        try {
            // ✅ UPDATE BLOB
            report.setPdfUrl(file.getBytes());
            report.setUploadedAt(LocalDateTime.now());

            FarmerLabReport updated = labReportRepository.save(report);

            log.info("Lab report updated successfully for surveyId={}", surveyId);
            return mapToUploadDTO(updated);

        } catch (Exception e) {
            log.error("Failed to update lab report for survey {}", surveyId, e);
            throw new RuntimeException("Failed to update lab report", e);
        }
    }

    /* ===================== DELETE ===================== */

    @Override
    @Transactional
    public void deleteLabReportBySurveyId(Long surveyId) {

        FarmerLabReport report = labReportRepository.findBySurvey_SurveyId(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Lab report not found for survey ID: " + surveyId));

        labReportRepository.delete(report);
        log.info("Lab report deleted successfully for surveyId={}", surveyId);
    }

    /* ===================== DOWNLOAD ===================== */

    @Override
    public byte[] downloadLabReport(Long surveyId) {

        FarmerLabReport report = labReportRepository.findBySurvey_SurveyId(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Lab report not found for survey ID: " + surveyId));

        // ✅ RETURN RAW PDF BYTES FROM MYSQL
        return report.getPdfUrl();
    }

    /* ===================== VALIDATIONS ===================== */

    private void validateSurveyId(Long surveyId) {
        if (surveyId == null || surveyId <= 0) {
            throw new IllegalArgumentException("Invalid survey ID");
        }
    }

    private void validatePdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }

        if (file.getContentType() == null ||
                !file.getContentType().equalsIgnoreCase("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        long maxSize = 5 * 1024 * 1024; // 5 MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("PDF size must be less than 5MB");
        }
    }

    /* ===================== DTO MAPPERS ===================== */


    private FarmerLabReportViewDTO mapToViewDTO(FarmerLabReport report) {
        FarmerLabReportViewDTO dto = new FarmerLabReportViewDTO();
        dto.setReportId(report.getReportId());
        dto.setSurveyId(report.getSurvey().getSurveyId());
        dto.setUploadedAt(report.getUploadedAt());
        dto.setDownloadUrl(
                "/api/v1/lab_report/download/" + report.getSurvey().getSurveyId()
        );

        return dto;
    }

    private FarmerLabReportUploadDTO mapToUploadDTO(FarmerLabReport report) {
        FarmerLabReportUploadDTO dto = new FarmerLabReportUploadDTO();
        dto.setReportId(report.getReportId());
        dto.setSurveyId(report.getSurvey().getSurveyId());
        return dto;
    }


}
