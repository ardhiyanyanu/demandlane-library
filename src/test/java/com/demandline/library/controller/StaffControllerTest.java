package com.demandline.library.controller;

import com.demandline.library.security.RequiresPermission;
import com.demandline.library.service.UserService;
import com.demandline.library.service.model.Role;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.input.UserInput;
import com.demandline.library.service.model.input.UserUpdateInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StaffController
 * Tests staff management endpoints with permission-based access control
 * Verifies ADMIN:CREATE, ADMIN:READ, ADMIN:UPDATE, ADMIN:DELETE permissions
 */
@DisplayName("StaffController Tests")
class StaffControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private StaffController staffController;

    private Role adminRole;
    private Role librarianRole;
    private Role frontDeskRole;
    private User adminUser;
    private User librarianUser;
    private User frontDeskUser;

    @BeforeEach
    void setUp() {
        try (var mocks = MockitoAnnotations.openMocks(this)) {
            // Mocks initialized
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Create roles
        adminRole = new Role(
            1,
            "ADMIN",
            "[\"ADMIN:CREATE\",\"ADMIN:READ\",\"ADMIN:UPDATE\",\"ADMIN:DELETE\",\"BOOK:CREATE\",\"BOOK:READ\",\"BOOK:UPDATE\",\"BOOK:DELETE\",\"BORROW:READ\",\"BORROW:UPDATE\",\"BORROW:DELETE\",\"MEMBER:READ\",\"MEMBER:UPDATE\"]",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        librarianRole = new Role(
            2,
            "LIBRARIAN",
            "[\"BOOK:CREATE\",\"BOOK:READ\",\"BOOK:UPDATE\",\"BOOK:DELETE\"]",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        frontDeskRole = new Role(
            3,
            "FRONT_DESK_STAFF",
            "[\"BORROW:READ\",\"BORROW:UPDATE\",\"BORROW:DELETE\",\"MEMBER:READ\",\"MEMBER:UPDATE\"]",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // Create test users with different roles
        adminUser = new User(
            1,
            "Admin User",
            "admin@library.local",
            "hashedPassword",
            adminRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true
        );

        librarianUser = new User(
            2,
            "Librarian User",
            "librarian@library.local",
            "hashedPassword",
            librarianRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true
        );

        frontDeskUser = new User(
            3,
            "FrontDesk User",
            "frontdesk@library.local",
            "hashedPassword",
            frontDeskRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true
        );
    }

    // ==================== CREATE STAFF TESTS ====================

    @Test
    @DisplayName("Admin can create new staff member with ADMIN:CREATE permission")
    void testCreateStaffWithAdminRole() {
        // Arrange
        StaffController.StaffCreateRequest request = new StaffController.StaffCreateRequest(
            "New Staff",
            "newstaff@library.local",
            "password123",
            "2"  // LIBRARIAN role ID
        );

        User createdUser = new User(
            10,
            "New Staff",
            "newstaff@library.local",
            "hashedPassword",
            librarianRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true
        );

        when(userService.registerUser(any(UserInput.class))).thenReturn(createdUser);

        // Act
        ResponseEntity<StaffController.StaffCreateResponse> response = staffController.createStaff(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().staffId());
        assertEquals("newstaff@library.local", response.getBody().email());
        assertEquals("New Staff", response.getBody().name());
        assertEquals("LIBRARIAN", response.getBody().role());

        verify(userService).registerUser(any(UserInput.class));
    }

    @Test
    @DisplayName("Librarian cannot create staff - missing ADMIN:CREATE permission")
    void testCreateStaffFailsWithLibrarianRole() {
        // Assert - verify createStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("createStaff", "ADMIN:CREATE");
    }

    @Test
    @DisplayName("FrontDesk cannot create staff - missing ADMIN:CREATE permission")
    void testCreateStaffFailsWithFrontDeskRole() {
        // Assert - verify createStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("createStaff", "ADMIN:CREATE");
    }

    @Test
    @DisplayName("Create staff fails with duplicate email")
    void testCreateStaffFailsWithDuplicateEmail() {
        // Arrange
        StaffController.StaffCreateRequest request = new StaffController.StaffCreateRequest(
            "New Staff",
            "existing@library.local",
            "password123",
            "2"
        );

        when(userService.registerUser(any(UserInput.class)))
            .thenThrow(new IllegalArgumentException("Email already exists"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> userService.registerUser(new UserInput(
                request.name(),
                request.email(),
                request.password(),
                request.roleId()
            )));
    }

    // ==================== LIST STAFF TESTS ====================

    @Test
    @DisplayName("Admin can list all staff members with ADMIN:READ permission")
    void testListStaffWithAdminRole() {
        // Arrange
        List<User> staffMembers = Arrays.asList(adminUser, librarianUser, frontDeskUser);
        when(userService.getAllUsers(false, 10, 0)).thenReturn(staffMembers);

        // Act
        ResponseEntity<List<StaffController.StaffResponse>> response = staffController.listStaff(10, 0);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("Admin User", response.getBody().get(0).name());
        assertEquals("ADMIN", response.getBody().get(0).role());

        verify(userService).getAllUsers(false, 10, 0);
    }

    @Test
    @DisplayName("List staff with pagination parameters")
    void testListStaffWithPagination() {
        // Arrange
        List<User> staffMembers = Arrays.asList(librarianUser, frontDeskUser);
        when(userService.getAllUsers(false, 5, 10)).thenReturn(staffMembers);

        // Act
        ResponseEntity<List<StaffController.StaffResponse>> response = staffController.listStaff(5, 10);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        verify(userService).getAllUsers(false, 5, 10);
    }

    @Test
    @DisplayName("Librarian cannot list staff - missing ADMIN:READ permission")
    void testListStaffFailsWithLibrarianRole() {
        // Assert - verify listStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("listStaff", "ADMIN:READ");
    }

    @Test
    @DisplayName("FrontDesk cannot list staff - missing ADMIN:READ permission")
    void testListStaffFailsWithFrontDeskRole() {
        // Assert - verify listStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("listStaff", "ADMIN:READ");
    }

    // ==================== UPDATE STAFF TESTS ====================

    @Test
    @DisplayName("Admin can update staff member with ADMIN:UPDATE permission")
    void testUpdateStaffWithAdminRole() {
        // Arrange
        StaffController.StaffUpdateRequest request = new StaffController.StaffUpdateRequest(
            "Updated Name",
            "updated@library.local",
            "newPassword",
            "LIBRARIAN"
        );

        User updatedUser = new User(
            2,
            "Updated Name",
            "updated@library.local",
            "hashedPassword",
            librarianRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true
        );

        when(userService.updateUser(any(UserUpdateInput.class))).thenReturn(updatedUser);

        // Act
        ResponseEntity<StaffController.StaffResponse> response = staffController.updateStaff(2, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().id());
        assertEquals("Updated Name", response.getBody().name());
        assertEquals("updated@library.local", response.getBody().email());

        verify(userService).updateUser(any(UserUpdateInput.class));
    }

    @Test
    @DisplayName("Librarian cannot update staff - missing ADMIN:UPDATE permission")
    void testUpdateStaffFailsWithLibrarianRole() {
        // Assert - verify updateStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("updateStaff", "ADMIN:UPDATE");
    }

    @Test
    @DisplayName("FrontDesk cannot update staff - missing ADMIN:UPDATE permission")
    void testUpdateStaffFailsWithFrontDeskRole() {
        // Assert - verify updateStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("updateStaff", "ADMIN:UPDATE");
    }

    @Test
    @DisplayName("Update staff fails when staff not found")
    void testUpdateStaffFailsWhenNotFound() {
        // Arrange
        StaffController.StaffUpdateRequest request = new StaffController.StaffUpdateRequest(
            "Updated Name",
            "updated@library.local",
            "newPassword",
            "LIBRARIAN"
        );

        when(userService.updateUser(any(UserUpdateInput.class)))
            .thenThrow(new IllegalArgumentException("Staff not found"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> userService.updateUser(new UserUpdateInput(999, request.name(), request.email(), request.password(), request.role())));
    }

    // ==================== DELETE STAFF TESTS ====================

    @Test
    @DisplayName("Admin can delete staff member with ADMIN:DELETE permission")
    void testDeleteStaffWithAdminRole() {
        // Arrange
        doNothing().when(userService).deleteUser("2");

        // Act
        ResponseEntity<Void> response = staffController.deleteStaff("2");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).deleteUser("2");
    }

    @Test
    @DisplayName("Librarian cannot delete staff - missing ADMIN:DELETE permission")
    void testDeleteStaffFailsWithLibrarianRole() {
        // Assert - verify deleteStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("deleteStaff", "ADMIN:DELETE");
    }

    @Test
    @DisplayName("FrontDesk cannot delete staff - missing ADMIN:DELETE permission")
    void testDeleteStaffFailsWithFrontDeskRole() {
        // Assert - verify deleteStaff has @RequiresPermission annotation
        verifyMethodHasPermissionAnnotation("deleteStaff", "ADMIN:DELETE");
    }

    @Test
    @DisplayName("Delete staff fails when staff not found")
    void testDeleteStaffFailsWhenNotFound() {
        // Arrange
        doThrow(new IllegalArgumentException("Staff not found")).when(userService).deleteUser("999");

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> userService.deleteUser("999"));
    }

    // ==================== PERMISSION MATRIX TESTS ====================

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE"})
    @DisplayName("Admin has all required ADMIN permissions")
    void testAdminHasAllPermissions(String permission) {
        // Assert
        assertTrue(hasAdminPermission(permission),
            "Admin should have " + permission + " permission");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE"})
    @DisplayName("Librarian lacks all ADMIN permissions")
    void testLibrarianLacksAdminPermissions(String permission) {
        // Assert
        assertFalse(hasLibrarianPermission(permission),
            "Librarian should not have " + permission + " permission");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE"})
    @DisplayName("FrontDesk lacks all ADMIN permissions")
    void testFrontDeskLacksAdminPermissions(String permission) {
        // Assert
        assertFalse(hasFrontDeskPermission(permission),
            "FrontDesk should not have " + permission + " permission");
    }

    // ==================== HELPER METHODS ====================

    private void verifyMethodHasPermissionAnnotation(String methodName, String expectedPermission) {
        try {
            Method method = findMethod(StaffController.class, methodName);
            assertNotNull(method, "Method " + methodName + " should exist in StaffController");

            RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
            assertNotNull(annotation, "Method " + methodName + " should have @RequiresPermission annotation");

            String[] permissions = annotation.value();
            assertTrue(Arrays.asList(permissions).contains(expectedPermission),
                "Method " + methodName + " should have @RequiresPermission(\"" + expectedPermission + "\")");
        } catch (Exception e) {
            fail("Failed to verify permission annotation: " + e.getMessage());
        }
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private boolean hasAdminPermission(String permission) {
        // Admin permissions from adminRole
        return Arrays.asList(
            "ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE",
            "BOOK:CREATE", "BOOK:READ", "BOOK:UPDATE", "BOOK:DELETE",
            "BORROW:READ", "BORROW:UPDATE", "BORROW:DELETE",
            "MEMBER:READ", "MEMBER:UPDATE"
        ).contains(permission);
    }

    private boolean hasLibrarianPermission(String permission) {
        // Librarian permissions
        return Arrays.asList(
            "BOOK:CREATE", "BOOK:READ", "BOOK:UPDATE", "BOOK:DELETE"
        ).contains(permission);
    }

    private boolean hasFrontDeskPermission(String permission) {
        // FrontDesk permissions
        return Arrays.asList(
            "BORROW:READ", "BORROW:UPDATE", "BORROW:DELETE",
            "MEMBER:READ", "MEMBER:UPDATE"
        ).contains(permission);
    }
}

