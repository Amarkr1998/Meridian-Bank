package com.meridianbank.kyc.web.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterCustomerRequest(
        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,72}$",
                message = "Password must be at least 8 characters and include an uppercase letter, "
                        + "a lowercase letter, a digit, and a special character")
        String password,

        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,

        @NotNull @Past LocalDate dateOfBirth,

        @NotBlank @Size(max = 30) String phone,

        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country
) {
}
