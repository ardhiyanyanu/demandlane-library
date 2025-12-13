package com.demandline.library.controller;

import com.demandline.library.security.JwtUtil;
import com.demandline.library.service.MemberService;
import com.demandline.library.service.UserService;
import com.demandline.library.service.model.Member;
import com.demandline.library.service.model.input.MemberInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * Authentication Controller
 * Handles user authentication and member self-registration endpoints
 */
@RestController
@RequestMapping("/library/auth")
@Slf4j
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {
    Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final MemberService memberSerice;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, MemberService memberService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.memberSerice = memberService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

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
        logger.info("Login attempt for email: {}", request.email());

        try {
            // Find user by email
            var userOptional = userService.getUserByEmail(request.email());

            if (userOptional.isEmpty()) {
                logger.warn("User not found: {}", request.email());
                userService.trackLoginFailure();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("", "", "", 0L));
            }

            var user = userOptional.get();

            // Check if user is active
            if (!user.isActive()) {
                logger.warn("User account is inactive: {}", request.email());
                userService.trackLoginFailure();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("", "", "", 0L));
            }

            // Validate password
            if (!passwordEncoder.matches(request.password(), user.password())) {
                logger.warn("Invalid password for user: {}", request.email());
                userService.trackLoginFailure();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("", "", "", 0L));
            }

            // Generate JWT token
            String role = user.role().name();
            var permissions = user.role().permissions();

            // Parse permissions from JSON string (stored as JSON array in database)
            java.util.List<String> permissionsList = parsePermissions(permissions);

            String token = jwtUtil.generateToken(user.id(), user.email(), role, permissionsList);

            logger.info("User logged in successfully: {}", request.email());
            userService.trackLoginSuccess();

            return ResponseEntity.ok(
                new LoginResponse(token, user.email(), role, 86400000L / 1000) // 24 hours in seconds
            );

        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage());
            userService.trackLoginFailure();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new LoginResponse("", "", "", 0L));
        }
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
        logger.info("Member registration attempt for email: {}", request.email());

        try {
            // Check if email already exists
            if (userService.isEmailRegistered(request.email())) {
                logger.warn("Email already exists: {}", request.email());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MemberRegistrationResponse(null, request.email(), request.name(), request.address(), request.phoneNumber(), "Email already registered"));
            }

            // Password should be encoded
            String encodedPassword = passwordEncoder.encode(request.password());

            var newMember = memberSerice.createMember(new MemberInput(
                request.name(),
                request.email(),
                encodedPassword,
                request.address(),
                request.phoneNumber()
            ));
            logger.info("Member registered successfully: {}", request.email());

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MemberRegistrationResponse(newMember, "Member registered successfully"));

        } catch (Exception e) {
            logger.error("Registration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MemberRegistrationResponse(null, request.email(), request.name(), request.address(), request.phoneNumber(), "Registration failed: " + e.getMessage()));
        }
    }

    /**
     * Parse permissions from JSON string format
     * Example: ["ADMIN:CREATE","ADMIN:READ"] -> List<String>
     */
    private java.util.List<String> parsePermissions(String permissionsJson) {
        try {
            if (permissionsJson == null || permissionsJson.isEmpty()) {
                return Arrays.asList();
            }

            // Remove brackets and split by comma
            String cleaned = permissionsJson.replaceAll("[\\[\\]\"]", "");
            String[] permissions = cleaned.split(",");

            // Trim whitespace and remove empty strings
            return Arrays.stream(permissions)
                    .map(String::trim)  // Add this to remove leading/trailing spaces
                    .filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            logger.warn("Error parsing permissions: {}", e.getMessage());
            return Arrays.asList();
        }
    }

    // Request/Response DTOs
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
        String password,
        String address,
        String phoneNumber
    ) {}

    public record MemberRegistrationResponse(
        Integer memberId,
        String email,
        String name,
        String address,
        String phoneNumber,
        String message
    ) {
        public MemberRegistrationResponse(Member member, String message) {
            this(member.id(), member.user().email(), member.user().name(), member.address(), member.phoneNumber(), message);
        }
    }
}

