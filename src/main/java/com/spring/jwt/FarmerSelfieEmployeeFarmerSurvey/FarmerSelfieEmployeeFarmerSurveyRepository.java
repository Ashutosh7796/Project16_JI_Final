package com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey;

import com.spring.jwt.Enums.PhotoType;
import com.spring.jwt.entity.FarmerSelfieEmployeeFarmerSurvey;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FarmerSelfieEmployeeFarmerSurveyRepository
        extends JpaRepository<FarmerSelfieEmployeeFarmerSurvey, Long> {

    boolean existsBySurvey_SurveyId(Long surveyId);

//    Optional<FarmerSelfieEmployeeFarmerSurvey>findBySurvey_SurveyId(Long surveyId);

    Optional<FarmerSelfieEmployeeFarmerSurvey>
    findByFarmerSelfieEmployeeFarmerSurveyIdAndPhotoType(
            Long selfieId, PhotoType photoType);

    Optional<FarmerSelfieEmployeeFarmerSurvey>
    findBySurvey_SurveyIdAndPhotoType(
            Long surveyId, PhotoType photoType);


    boolean existsBySurvey_SurveyIdAndPhotoType(Long surveyId, PhotoType photoType);

    @Transactional
    @Modifying
    @Query("""
        DELETE FROM FarmerSelfieEmployeeFarmerSurvey f
        WHERE f.survey.surveyId = :surveyId
    """)
    void deleteBySurveyId(@Param("surveyId") Long surveyId);

    List<FarmerSelfieEmployeeFarmerSurvey> findBySurvey_SurveyId(Long surveyId);
}
