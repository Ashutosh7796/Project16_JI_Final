package com.spring.jwt.EmployeeFarmerSurvey;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SurveyStatusCountDTO {

    private long pendingCount;
    private long completedCount;
}
