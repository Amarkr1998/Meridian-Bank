package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.domain.CustomerStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        CustomerStatus status,
        boolean contactVerified,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getEmail(), c.getFirstName(), c.getLastName(),
                c.getDateOfBirth(), c.getPhone(), c.getAddressLine1(), c.getAddressLine2(), c.getCity(),
                c.getState(), c.getPostalCode(), c.getCountry(), c.getStatus(), c.isContactVerified(),
                c.getCreatedAt());
    }
}
