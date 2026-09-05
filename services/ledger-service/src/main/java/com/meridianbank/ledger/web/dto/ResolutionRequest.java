package com.meridianbank.ledger.web.dto;

import jakarta.validation.constraints.Size;

public record ResolutionRequest(@Size(max = 1000) String notes) {
}
