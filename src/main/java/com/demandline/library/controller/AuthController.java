package com.demandline.library.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles user authentication and member self-registration endpoints
 */
@RestController
@RequestMapping("/library/auth")
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    @PostMapping("/login")
    @Operation(
        summary = "User Login",
        description = "Authenticate user with email and password. Returns JWT token for authenticated users."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // TODO: Implement login logic
        return ResponseEntity.ok(new LoginResponse());
    }

    @PostMapping("/member/register")
    @Operation(
        summary = "Member Self-Registration",
        description = "Allow members to self-register through the system. Creates a new member account with MEMBER role."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Member registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or email already exists"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<MemberRegistrationResponse> registerMember(@RequestBody MemberRegistrationRequest request) {
        // TODO: Implement member registration logic
        return ResponseEntity.status(HttpStatus.CREATED).body(new MemberRegistrationResponse());
    }

    // Response DTOs
    public record LoginRequest(String email, String password) {}

    public record LoginResponse(
        String token,
        String email,
        String role,
        Long expiresIn
    ) {
        public LoginResponse() {
            this("", "", "", 0L);
        }
    }

    public record MemberRegistrationRequest(
        String name,
        String email,
        String password
    ) {}

    public record MemberRegistrationResponse(
        Integer memberId,
        String email,
        String name,
        String message
    ) {
        public MemberRegistrationResponse() {
            this(null, "", "", "");
        }
    }
}

