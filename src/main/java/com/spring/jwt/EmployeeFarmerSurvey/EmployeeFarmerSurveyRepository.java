package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.Enums.FormStatus;
import com.spring.jwt.entity.EmployeeFarmerSurvey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeFarmerSurveyRepository extends JpaRepository<EmployeeFarmerSurvey, Long> {


    Optional<EmployeeFarmerSurvey> findByFormNumber(String formNumber);

    boolean existsByFormNumber(String formNumber);

    Optional<EmployeeFarmerSurvey>
    findTopByFormNumberStartingWithOrderByFormNumberDesc(String prefix);

    boolean existsByFarmerMobile(String farmerMobile);


    Page<EmployeeFarmerSurvey> findByUser_UserId(
            Long userId,
            Pageable pageable
    );


    Page<EmployeeFarmerSurvey> findByFormStatus(
            FormStatus status,
            Pageable pageable
    );

    Page<EmployeeFarmerSurvey> findByFormStatusAndUser_UserId(
            FormStatus status,
            Long userId,
            Pageable pageable
    );


    long countByFormStatus(FormStatus formStatus);

    long countByFormStatusAndUser_UserId(
            FormStatus formStatus,
            Long userId
    );
}
