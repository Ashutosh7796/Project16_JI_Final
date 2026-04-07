package com.spring.jwt.entity;

import com.spring.jwt.Enums.PhotoType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "farmer_selfie_employee_farmer_survey",
        indexes = {
                @Index(
                        name = "idx_farmer_selfie_survey_id",
                        columnList = "survey_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_survey_photo_type",
                        columnNames = {"survey_id", "photo_type"}
                )
        }
)

public class FarmerSelfieEmployeeFarmerSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long farmerSelfieEmployeeFarmerSurveyId;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private EmployeeFarmerSurvey survey;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false)
    private PhotoType photoType;


    private LocalDateTime takenAt = LocalDateTime.now();
}