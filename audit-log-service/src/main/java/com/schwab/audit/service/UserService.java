package com.schwab.audit.service;

import com.schwab.audit.dto.request.RegisterRequest;
import com.schwab.audit.dto.response.RegisterResponse;
import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import com.schwab.audit.exception.BadRequestException;
import com.schwab.audit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for user management operations.
 * 
 * Provides business logic for user registration and account management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new user with the provided credentials.
     * 
     * Performs the following validations:
     * - Username must not already exist
     * - Role must be a valid UserRole enum value
     * - Password is encoded with BCrypt before storage
     * 
     * @param registerRequest the registration request containing username, password, and role
     * @return RegisterResponse containing the newly created user's information
     * @throws BadRequestException if username already exists or role is invalid
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        log.info("Registering new user: {}", registerRequest.getUsername());

        // Validate username is not already taken
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            log.warn("Registration failed: Username already exists - {}", registerRequest.getUsername());
            throw new BadRequestException("Username '" + registerRequest.getUsername() + "' is already taken. Please choose a different username.");
        }

        // Validate and parse role
        UserRole userRole;
        try {
            userRole = UserRole.fromString(registerRequest.getRole());
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: Invalid role - {}", registerRequest.getRole());
            throw new BadRequestException("Invalid role: " + registerRequest.getRole() + ". Valid roles are: AUDIT_WRITER, AUDITOR, ADMIN");
        }

        // Create and save new user
        User newUser = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(userRole)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully: {} with role: {}", savedUser.getUsername(), savedUser.getRole());

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .role(savedUser.getRole().name())
                .message("User registered successfully")
                .build();
    }
}
