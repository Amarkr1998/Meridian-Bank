package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class KycRecordNotFoundException extends KycException {
    public KycRecordNotFoundException() {
        super("KYC_RECORD_NOT_FOUND", HttpStatus.NOT_FOUND, "KYC record not found");
    }
}
