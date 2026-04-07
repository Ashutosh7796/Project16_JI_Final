package com.spring.jwt.FarmerLabReport;

import com.spring.jwt.entity.FarmerLabReport;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FarmerLabReportRepository extends JpaRepository<FarmerLabReport, Long> {

    boolean existsBySurvey_SurveyId(Long surveyId);

    Optional<FarmerLabReport> findBySurvey_SurveyId(Long surveyId);

    @Transactional
    @Modifying
    @Query("""
        DELETE FROM FarmerLabReport r
        WHERE r.survey.surveyId = :surveyId
    """)
    void deleteBySurveyId(@Param("surveyId") Long surveyId);
}
