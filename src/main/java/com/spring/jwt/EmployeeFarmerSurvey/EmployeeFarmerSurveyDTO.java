package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.Enums.FormStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFarmerSurveyDTO {

    private Long surveyId;

    @NotBlank(message = "Form Number is required")
    @Schema(description = "Form No Must Be Unique", example = "FORM-1001")
    private String formNumber;

    @NotBlank(message = "Farmer name is required")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z ]{1,49}$",
            message = "Farmer name must contain only letters and spaces (2–50 characters), no numbers or special characters"
    )
    @Schema(description = "Name of the farmer", example = "ramesh patil")
    private String farmerName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Please enter valid 10 digit mobile number (e.g. 9766431234)"
    )
    @Schema(description = "Farmer mobile number", example = "9766431234")
    private String farmerMobile;

    @NotBlank(message = "Farmer area is required")
    @Pattern(
            regexp = "^\\d+(\\.\\d{1,2})?$",
            message = "Land area must be a valid number (example: 2 or 2.5)"
    )
    @Schema(description = "Total land area owned by farmer (in Acres)", example = "2.5")
    private String landArea;

    @NotBlank(message = "village is required")
    @Schema(description = "Farmer village", example = "Nashik")
    private String village;

    @NotBlank(message = "Address is required")
    @Schema(description = "Farmer address", example = "Village Mulshi, Pune")
    private String address;

    @NotBlank(message = "Taluka is required")
    @Schema(description = "Name of Taluka", example = "Mulshi")
    private String taluka;

    @NotBlank(message = "District is required")
    @Schema(description = "Name of District", example = "Pune")
    private String district;

    /* ========= NEW FIELDS (From Entity) ========= */

    @Schema(description = "Additional farm related information",
            example = "Irrigated land with drip system")
    private String farmInformation;

    @Schema(description = "Crop details grown by farmer",
            example = "[\"Wheat\", \"Sugarcane\"]")
    private List<String> cropDetails;

    @Schema(description = "Livestock details owned by farmer",
            example = "[\"Cow\", \"Buffalo\"]")
    private List<String> livestockDetails;

    @Schema(description = "Production equipment used by farmer",
            example = "[\"Tractor\", \"Sprayer\"]")
    private List<String> productionEquipment;


    @NotNull(message = "Sample status is required")
    @Schema(description = "Sample collected status", example = "true")
    private Boolean sampleCollected;


    @NotNull(message = "User ID (Employee ID) is required")
    @Schema(description = "Employee(User) ID who filled the survey",
            example = "10005")
    private Long userId;

    private FormStatus formStatus;

    private FarmerSelfieDTO farmerSelfie;

    private LocalDateTime createdAt;
}
