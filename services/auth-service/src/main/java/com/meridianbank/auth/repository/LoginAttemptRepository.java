package com.meridianbank.auth.repository;

import com.meridianbank.auth.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
}
