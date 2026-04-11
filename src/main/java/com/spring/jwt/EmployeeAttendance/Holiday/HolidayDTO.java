package com.spring.jwt.EmployeeAttendance.Holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class HolidayDTO {

    private Long id;

    @NotNull(message = "Holiday date is required")
    @Schema(example = "2026-01-26")
    private LocalDate holidayDate;

    @NotBlank(message = "Holiday description is required")
    @Schema(example = "Republic Day")
    private String description;
}
