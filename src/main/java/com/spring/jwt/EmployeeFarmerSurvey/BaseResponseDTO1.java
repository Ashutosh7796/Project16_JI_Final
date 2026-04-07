package com.spring.jwt.EmployeeFarmerSurvey;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseDTO1<T> {

    private String code;
    private String message;
    private T data;
}
