package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "farmer_lab_report",
        indexes = {
                @Index(name = "idx_lab_report_survey_id", columnList = "survey_id")
        }
)
public class FarmerLabReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private EmployeeFarmerSurvey survey;

    @Lob
    @Column(name = "pdf_url", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] pdfUrl;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
