package com.spring.jwt.ProductBuyPending;


import lombok.*;

import jakarta.validation.constraints.*;

@Data
public class CreateOrderRequestDto {

    @NotNull
    private Long productId;

    @NotNull
    private Long userId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Minimum quantity is 1")
    @Max(value = 11, message = "Maximum quantity is 11")
    private Integer quantity;

    @NotBlank
    private String deliveryAddress;

    @NotBlank
    private String customerName;

    @NotBlank
    @Size(min = 10, max = 10)
    private String contactNumber;
}