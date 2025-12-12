package com.demandline.library.controller;

import com.demandline.library.security.RequiresPermission;
import com.demandline.library.service.UserService;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.input.UserInput;
import com.demandline.library.service.model.input.UserUpdateInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin Staff Management Controller
 * Handles library staff creation and management endpoints
 * Requires ADMIN role for all operations
 */
@RestController
@RequestMapping("/library/admin/staff")
@Tag(name = "Admin - Staff Management", description = "Library staff account management endpoints (Admin only)")
@SecurityRequirement(name = "Bearer Authentication")
public class StaffController {
    private final UserService userService;

    public StaffController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/create")
    @RequiresPermission("ADMIN:CREATE")
    @Operation(
        summary = "Create New Library Staff Account",
        description = "Create a new library staff account with specific role. Admin can assign roles: Librarian, Front Desk Staff. Password is provided by admin.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Staff account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or email already exists"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN:CREATE required)"),
        @ApiResponse(responseCode = "409", description = "Email already exists in system")
    })
    public ResponseEntity<StaffCreateResponse> createStaff(@RequestBody StaffCreateRequest request) {
        var newUser = userService.registerUser(new UserInput(
            request.name(),
            request.email(),
            request.password(),
            request.roleId()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(new StaffCreateResponse(newUser));
    }

    @GetMapping
    @RequiresPermission("ADMIN:READ")
    @Operation(
        summary = "List All Library Staff",
        description = "Retrieve list of all library staff members with their roles and details.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Staff list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN:READ required)")
    })
    public ResponseEntity<List<StaffResponse>> listStaff(
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset
    ) {
        var users = userService.getAllUsers(false, limit, offset);
        return ResponseEntity.ok(users.stream().map(StaffResponse::new).toList());
    }

    @PutMapping("/{id}")
    @RequiresPermission("ADMIN:UPDATE")
    @Operation(
        summary = "Update Library Staff Information",
        description = "Update staff member information such as name, email, or role assignment.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Staff information updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN:UPDATE required)"),
        @ApiResponse(responseCode = "404", description = "Staff member not found")
    })
    public ResponseEntity<StaffResponse> updateStaff(
        @Parameter(description = "Staff member ID") @PathVariable Integer id,
        @RequestBody StaffUpdateRequest request) {
        var updatedUser = userService.updateUser(new UserUpdateInput(
                id,
                request.name(),
                request.email(),
                request.password(),
                request.role()
        ));
        return ResponseEntity.ok(new StaffResponse(updatedUser));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("ADMIN:DELETE")
    @Operation(
        summary = "Deactivate Library Staff Account",
        description = "Deactivate a library staff account. The account is marked as inactive instead of being deleted.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Staff account deactivated successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN:DELETE required)"),
        @ApiResponse(responseCode = "404", description = "Staff member not found")
    })
    public ResponseEntity<Void> deleteStaff(@Parameter(description = "Staff member ID") @PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    // Request/Response DTOs
    public record StaffCreateRequest(
        String name,
        String email,
        String password,
        String roleId
    ) {}

    public record StaffUpdateRequest(
        String name,
        String email,
        String password,
        String role
    ) {}

    public record StaffCreateResponse(
        Integer staffId,
        String email,
        String name,
        String role,
        String message
    ) {
        public StaffCreateResponse(User user) {
            this(user.id(), user.email(), user.name(), user.role().name(), "");
        }
    }

    public record StaffResponse(
        Integer id,
        String name,
        String email,
        String role,
        Boolean isActive
    ) {
        public StaffResponse(User user) {
            this(user.id(), user.name(), user.email(), user.role().name(), user.isActive());
        }
    }
}

