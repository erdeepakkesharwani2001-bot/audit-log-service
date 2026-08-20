package com.schwab.audit.controller;

import com.schwab.audit.dto.request.LoginRequest;
import com.schwab.audit.dto.request.RegisterRequest;
import com.schwab.audit.dto.response.LoginResponse;
import com.schwab.audit.dto.response.RegisterResponse;
import com.schwab.audit.entity.User;
import com.schwab.audit.exception.BadRequestException;
import com.schwab.audit.exception.UnauthorizedException;
import com.schwab.audit.security.JwtService;
import com.schwab.audit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for authentication operations.
 * 
 * Provides JWT-based authentication and user registration for all user roles.
 * 
 * API Endpoints:
 * - POST /api/v1/auth/register: Register a new user account
 * - POST /api/v1/auth/login: Authenticate user and receive JWT token
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    /**
     * Registers a new user account.
     * 
     * Request body:
     * {
     *   "username": "string",
     *   "password": "string",
     *   "role": "AUDIT_WRITER|AUDITOR|ADMIN"
     * }
     * 
     * Response:
     * {
     *   "userId": 1,
     *   "username": "newuser",
     *   "role": "AUDITOR",
     *   "message": "User registered successfully"
     * }
     * 
     * @param registerRequest credentials and role for the new user
     * @return RegisterResponse with newly created user information
     * @throws BadRequestException if username already exists or validation fails
     */
    @PostMapping("/register")
    @Operation(
        summary = "User registration",
        description = "Register a new user account with username, password, and role. Returns user information on success."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "User registered successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RegisterResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing fields, username already taken, invalid role, or password too short)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            log.info("Processing registration request for username: {}", registerRequest.getUsername());
            
            RegisterResponse response = userService.registerUser(registerRequest);
            
            log.info("User registered successfully: {}", registerRequest.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (BadRequestException e) {
            log.warn("Registration validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration", e);
            throw new RuntimeException("Registration failed due to unexpected error", e);
        }
    }

    /**
     * Authenticates a user and returns a JWT token.
     * 
     * Request body:
     * {
     *   "username": "string",
     *   "password": "string"
     * }
     * 
     * Response:
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "tokenType": "Bearer",
     *   "expiresIn": 86400,
     *   "username": "user123",
     *   "role": "AUDITOR"
     * }
     * 
     * @param loginRequest credentials (username and password)
     * @return LoginResponse with JWT token on success
     * @throws UnauthorizedException if credentials are invalid
     */
    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticate user with username and password. Returns JWT token for subsequent API calls."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful, JWT token returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body (missing or empty username/password)"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials (username not found or password mismatch)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Attempt authentication
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            // Get authenticated user
            User user = (User) authentication.getPrincipal();

            // Generate JWT token
            String token = jwtService.generateToken(user);
            long expiresIn = jwtService.extractExpirySeconds(token);

            // Log successful login
            log.info("User {} successfully authenticated", user.getUsername());

            // Build and return response
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(expiresIn)
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .build();

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            log.warn("Authentication failed for user: {}", loginRequest.getUsername());
            throw new UnauthorizedException("Invalid username or password", e);
        } catch (Exception e) {
            log.error("Unexpected error during authentication", e);
            throw new RuntimeException("Authentication failed due to unexpected error", e);
        }
    }
}
