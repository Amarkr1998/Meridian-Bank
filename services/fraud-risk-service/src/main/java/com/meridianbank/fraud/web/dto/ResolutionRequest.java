package com.meridianbank.fraud.web.dto;

import jakarta.validation.constraints.Size;

public record ResolutionRequest(@Size(max = 1000) String notes) {
}
