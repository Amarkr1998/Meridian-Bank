package com.meridianbank.kyc.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitKycRequest(
        @NotBlank @Size(max = 100) String nationality,
        @NotBlank @Size(max = 150) String occupation,
        @NotEmpty @Valid List<DocumentEntry> documents
) {
}
