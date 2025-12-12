package com.demandline.library.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * JWT Authentication Details
 * Stores additional user information extracted from JWT token
 */
@Getter
@AllArgsConstructor
public class JwtAuthenticationDetails {
    private Integer userId;
    private String email;
    private String role;
    private List<String> permissions;
}

