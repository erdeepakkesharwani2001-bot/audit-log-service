package com.schwab.audit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for successful login (POST /auth/login).
 * Contains JWT token and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;  // Token expiry in seconds
    private String username;
    private String role;
}
