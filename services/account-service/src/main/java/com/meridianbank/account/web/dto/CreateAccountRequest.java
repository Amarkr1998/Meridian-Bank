package com.meridianbank.account.web.dto;

import com.meridianbank.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotNull AccountType accountType) {
}
