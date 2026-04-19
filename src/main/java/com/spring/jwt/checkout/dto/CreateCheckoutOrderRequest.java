package com.spring.jwt.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateCheckoutOrderRequest {

    @NotEmpty
    @Valid
    private List<CheckoutLineRequest> lines;

    /** When set, customerName / contactNumber / deliveryAddress are taken from this saved row (must belong to user). */
    private Long savedAddressId;

    private String customerName;

    private String contactNumber;

    private String deliveryAddress;

    @AssertTrue(message = "Either savedAddressId or customer name, contact number, and delivery address are required")
    public boolean isAddressValid() {
        if (savedAddressId != null) {
            return true;
        }
        return customerName != null && !customerName.isBlank()
                && contactNumber != null && !contactNumber.isBlank()
                && deliveryAddress != null && !deliveryAddress.isBlank();
    }
}
