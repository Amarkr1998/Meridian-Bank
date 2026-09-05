package com.meridianbank.kyc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only contact/address fields are mutable post-registration — identity fields (name, date of
 *  birth) are not, to keep them consistent with what was KYC-verified. */
public record UpdateCustomerRequest(
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country
) {
}
