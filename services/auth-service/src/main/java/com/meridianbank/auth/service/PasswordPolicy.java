package com.meridianbank.auth.service;

/**
 * Minimum password strength requirement, shared by registration, staff provisioning, and
 * password reset. At least 8 characters with one uppercase, one lowercase, one digit, and one
 * special character.
 */
public final class PasswordPolicy {

    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,72}$";

    public static final String MESSAGE =
            "Password must be at least 8 characters and include an uppercase letter, "
                    + "a lowercase letter, a digit, and a special character";

    private PasswordPolicy() {
    }
}
