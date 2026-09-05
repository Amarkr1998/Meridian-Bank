package com.meridianbank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.admin-bootstrap")
public record AdminBootstrapProperties(boolean enabled, String email, String password) {
}
