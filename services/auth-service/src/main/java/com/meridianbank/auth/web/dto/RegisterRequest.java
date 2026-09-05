package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-service customer registration. Always creates a CUSTOMER-role identity. */
public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password
) {
}
