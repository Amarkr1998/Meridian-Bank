package com.meridianbank.auth.service;

import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.exception.EmailAlreadyRegisteredException;
import com.meridianbank.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates identities. Self-service {@link #registerCustomer} always assigns {@code CUSTOMER};
 * every other role is provisioned only through {@link #createStaffUser}, which callers must gate
 * behind {@code ADMIN} authorization (enforced in {@code AuthController}) — this is the boundary
 * between public registration and internal staff provisioning. See docs/adr/0010-rbac.md.
 */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerCustomer(String email, String rawPassword) {
        return create(email, rawPassword, UserRole.CUSTOMER);
    }

    @Transactional
    public User createStaffUser(String email, String rawPassword, UserRole role) {
        return create(email, rawPassword, role);
    }

    private User create(String rawEmail, String rawPassword, UserRole role) {
        String email = rawEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), role);
        return userRepository.save(user);
    }
}
