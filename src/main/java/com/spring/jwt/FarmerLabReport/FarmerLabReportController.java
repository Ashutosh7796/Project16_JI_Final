package com.spring.jwt.FarmerLabReport;

import com.spring.jwt.EmployeeFarmerSurvey.BaseResponseDTO1;
import com.spring.jwt.EmployeeFarmerSurvey.SurveyIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing Farmer Lab Reports.
 *
 * <p>
 * This controller exposes APIs to upload, view, update, delete,
 * and download lab report PDF documents associated with
 * {@code EmployeeFarmerSurvey}.
 * </p>
 *
 * <p>
 * All successful responses are wrapped using {@link BaseResponseDTO1}
 * to maintain a consistent API response structure across the application.
 * </p>
 *
 * Base URL:
 * <pre>
 * /api/v1/lab-report
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/lab_report")
@RequiredArgsConstructor
public class FarmerLabReportController {

    private final FarmerLabReportService labReportService;
    private final SurveyIdResolver surveyIdResolver;

    /* ===================== UPLOAD ===================== */

    @PostMapping(
            value = "/upload/{surveyId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<BaseResponseDTO1<FarmerLabReportUploadDTO>> upload(
            @PathVariable String surveyId,
            @RequestParam MultipartFile file) {
        Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);

        FarmerLabReportUploadDTO response =
                labReportService.uploadLabReport(internalSurveyId, file);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Lab report uploaded successfully",
                        response
                )
        );
    }

    /* ===================== VIEW ===================== */

    @GetMapping("/view/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<FarmerLabReportViewDTO>> view(
            @PathVariable String surveyId) {
        Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);

        FarmerLabReportViewDTO response =
                labReportService.viewLabReport(internalSurveyId);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Lab report fetched successfully",
                        response
                )
        );
    }


    @GetMapping("/get/{reportId}")
    public ResponseEntity<BaseResponseDTO1<FarmerLabReportViewDTO>> getById(
            @PathVariable Long reportId) {

        FarmerLabReportViewDTO response =
                labReportService.getLabReportById(reportId);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Lab report fetched successfully",
                        response
                )
        );
    }

    /* ===================== UPDATE ===================== */

    @PatchMapping(
            value = "/update/{surveyId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<BaseResponseDTO1<FarmerLabReportUploadDTO>> update(
            @PathVariable String surveyId,
            @RequestParam MultipartFile file) {
        Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);

        FarmerLabReportUploadDTO response =
                labReportService.updateLabReportBySurveyId(internalSurveyId, file);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Lab report updated successfully",
                        response
                )
        );
    }

    /* ===================== DELETE ===================== */

    @DeleteMapping("/delete/{surveyId}")
    public ResponseEntity<BaseResponseDTO1<Void>> delete(
            @PathVariable String surveyId) {
        Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);

        labReportService.deleteLabReportBySurveyId(internalSurveyId);

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Lab report deleted successfully",
                        null
                )
        );
    }

    /* ===================== DOWNLOAD ===================== */

    @GetMapping(
            value = "/download/{surveyId}",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> download(@PathVariable String surveyId) {
        Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);

        byte[] pdf = labReportService.downloadLabReport(internalSurveyId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=lab-report-" + surveyId + ".pdf"
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

}
