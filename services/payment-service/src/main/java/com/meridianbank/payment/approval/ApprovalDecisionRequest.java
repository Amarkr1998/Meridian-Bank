package com.meridianbank.payment.approval;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(@Size(max = 1000) String notes) {
}
