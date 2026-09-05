package com.meridianbank.auth.repository;

import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    Page<User> findByRole(UserRole role, Pageable pageable);
}
