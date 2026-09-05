package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ADMIN-only provisioning of an internal staff identity with an explicit role. */
public record CreateStaffUserRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotNull
        UserRole role
) {
}
