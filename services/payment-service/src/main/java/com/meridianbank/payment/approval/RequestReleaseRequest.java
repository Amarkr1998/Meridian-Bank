package com.meridianbank.payment.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestReleaseRequest(@NotBlank @Size(max = 500) String reason) {
}
