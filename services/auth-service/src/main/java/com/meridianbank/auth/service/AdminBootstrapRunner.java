package com.meridianbank.auth.service;

import com.meridianbank.auth.config.AdminBootstrapProperties;
import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a single ADMIN identity on first startup so there is a way into the ADMIN-only staff
 * provisioning endpoint at all — without this, nobody could ever create the first internal user.
 * Runs at most once per environment: skipped whenever any ADMIN user already exists. Credentials
 * come from {@code MERIDIAN_ADMIN_EMAIL}/{@code MERIDIAN_ADMIN_PASSWORD} — local/demo defaults
 * are documented in .env.example and must never be reused outside local development.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final UserProvisioningService provisioningService;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 UserProvisioningService provisioningService,
                                 AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Admin bootstrap skipped: an ADMIN user already exists");
            return;
        }
        provisioningService.createStaffUser(properties.email(), properties.password(), UserRole.ADMIN);
        log.info("Bootstrap ADMIN account created for email={} (password from configuration, not logged)",
                properties.email());
    }
}
