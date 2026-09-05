package com.meridianbank.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountStatusChangeRequest(@NotBlank @Size(max = 500) String reason) {
}
