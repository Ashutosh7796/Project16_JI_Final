package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SurveyIdResolver {

    private final EmployeeFarmerSurveyRepository surveyRepository;

    public Long resolveToInternalId(String surveyIdOrPublicId) {
        if (surveyIdOrPublicId == null || surveyIdOrPublicId.isBlank()) {
            throw new IllegalArgumentException("Survey ID must not be empty");
        }

        String resolvedInput = surveyIdOrPublicId.trim();

        // 1) Preferred public id format
        if (resolvedInput.startsWith("sur_")) {
            return surveyRepository.findBySurveyPublicId(resolvedInput)
                    .map(s -> s.getSurveyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Survey not found with ID: " + resolvedInput));
        }

        // 2) Backward compatibility for form numbers (e.g. 2026040001)
        // Try form number lookup first to avoid treating it as internal PK.
        return surveyRepository.findByFormNumber(resolvedInput)
                .map(s -> s.getSurveyId())
                .orElseGet(() -> resolveAsNumericId(resolvedInput));
    }

    private Long resolveAsNumericId(String surveyId) {
        try {
            Long internalId = Long.parseLong(surveyId);
            return surveyRepository.findById(internalId)
                    .map(s -> s.getSurveyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Survey not found with ID: " + surveyId));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid survey ID format: " + surveyId);
        }
    }
}
