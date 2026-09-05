package com.meridianbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs for the downstream services this gateway currently routes to — see RouteRegistry. */
@ConfigurationProperties(prefix = "meridian.gateway.services")
public record GatewayServiceUrls(
        String authService,
        String customerKycService,
        String accountService,
        String paymentService,
        String notificationService,
        String fraudRiskService,
        String ledgerService,
        String auditService
) {
}
