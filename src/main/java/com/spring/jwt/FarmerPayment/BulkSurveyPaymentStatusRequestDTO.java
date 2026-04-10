package com.spring.jwt.FarmerPayment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BulkSurveyPaymentStatusRequestDTO {

    @NotNull
    private List<String> surveyIds;
}
