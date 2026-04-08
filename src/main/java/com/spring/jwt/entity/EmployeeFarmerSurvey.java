package com.spring.jwt.entity;

import com.spring.jwt.Enums.FormStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "employee_farmer_survey",
        indexes = {
                @Index(name = "idx_emp_survey_form_number", columnList = "formNumber"),
                @Index(name = "idx_emp_survey_mobile", columnList = "farmerMobile"),
                @Index(name = "idx_emp_survey_user_id", columnList = "user_id"),
                @Index(name = "idx_emp_survey_district", columnList = "district")
        }
)
public class EmployeeFarmerSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long surveyId;

    @Column(unique = true,nullable = false)
    private String formNumber;

    @Column(nullable = false)
    private String farmerName;

    @Column(nullable = false,length = 10)
    private String farmerMobile;

    @Column(nullable = false)
    private String landArea;

    @Column(columnDefinition = "TEXT")
    private String village;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    private String taluka;

    @Column(nullable = false)
    private String district;

    private String farmInformation;

    @ElementCollection
    @CollectionTable(
            name = "survey_crop_details",
            joinColumns = @JoinColumn(name = "survey_id")
    )
    @Column(name = "crop")
    private List<String> cropDetails;

    @ElementCollection
    @CollectionTable(
            name = "survey_livestock_details",
            joinColumns = @JoinColumn(name = "survey_id")
    )
    @Column(name = "livestock")
    private List<String> livestockDetails;

    @ElementCollection
    @CollectionTable(
            name = "survey_production_equipment",
            joinColumns = @JoinColumn(name = "survey_id")
    )
    @Column(name = "equipment")
    private List<String> productionEquipment;

    @Column(nullable = false)
    private Boolean sampleCollected = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false
    )
    private User user;
    @Enumerated(EnumType.STRING)
    private FormStatus formStatus;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}