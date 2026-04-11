package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.Enums.FormStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmployeeFarmerSurveyService {

    EmployeeFarmerSurveyDTO createSurvey(EmployeeFarmerSurveyRegDTO dto);

    EmployeeFarmerSurveyDTO getSurveyById(Long surveyId);

    public Page<EmployeeFarmerSurveyDTO> getAllSurveys(Pageable pageable);

    EmployeeFarmerSurveyDTO updateSurvey(Long surveyId, EmployeeFarmerSurveyDTO dto);

    void deleteSurvey(Long surveyId);

    Page<EmployeeFarmerSurveyDTO> getByUserIdSurveys(Long userId, Pageable pageable);

    Page<EmployeeFarmerSurveyDTO> getMySurveys(Pageable pageable);

    SurveyStatusCountDTO getAllSurveyStatusCount();

    SurveyStatusCountDTO getSurveyStatusCountByLoggedInUser();

    Page<EmployeeFarmerSurveyDTO> getAllSurveysByStatus(FormStatus status, Pageable pageable);

    Page<EmployeeFarmerSurveyDTO> getMySurveysByStatus(FormStatus status, Pageable pageable);
}
