package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Synthetic document metadata only — never a real identity document. */
public record DocumentEntry(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 100) String documentReference
) {
}
