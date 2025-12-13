package com.demandline.library.service;

import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.UserRepository;
import com.demandline.library.service.model.input.UserInput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class UserEntityServiceIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testRegisterAndRetrieveUser() {
        // make sure roles from migration exist
        assertThat(roleRepository.findByName("ADMIN")).isPresent();

        var input = new UserInput("Test User", "test.user@local", "password123", String.valueOf(roleRepository.findByName("ADMIN").get().getId()));
        var user = userService.registerUser(input);

        assertThat(user).isNotNull();
        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo("test.user@local");
        assertThat(user.name()).isEqualTo("Test User");
        assertThat(user.isActive()).isTrue();
        assertThat(user.role()).isNotNull();
        assertThat(user.role().name()).isEqualTo("ADMIN");

        var fromRepo = userService.getUserByEmail("test.user@local");
        assertThat(fromRepo).isPresent();
        assertThat(fromRepo.get().email()).isEqualTo("test.user@local");
    }

    @Test
    void testRegisterUserWithInvalidRole() {
        var input = new UserInput("Invalid User", "invalid@local", "password123", "999");

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> userService.registerUser(input)
        ).getMessage()).isEqualTo("Role not found");
    }

    @Test
    void testIsEmailRegistered() {
        var adminRole = roleRepository.findByName("ADMIN").get();
        var input = new UserInput("Email Test User", "emailtest@local", "password123", String.valueOf(adminRole.getId()));

        assertThat(userService.isEmailRegistered("emailtest@local")).isFalse();

        userService.registerUser(input);

        assertThat(userService.isEmailRegistered("emailtest@local")).isTrue();
        assertThat(userService.isEmailRegistered("nonexistent@local")).isFalse();
    }

    @Test
    void testUpdateUser() {
        // Register a user first
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var input = new UserInput("Original Name", "update.test@local", "password123", String.valueOf(librarianRole.getId()));
        var user = userService.registerUser(input);

        // Update user details
        var adminRole = roleRepository.findByName("ADMIN").get();
        var updateInput = new com.demandline.library.service.model.input.UserUpdateInput(
            user.id(),
            "Updated Name",
            "updated.email@local",
            "newpassword456",
            String.valueOf(adminRole.getId())
        );

        var updatedUser = userService.updateUser(updateInput);

        assertThat(updatedUser.name()).isEqualTo("Updated Name");
        assertThat(updatedUser.email()).isEqualTo("updated.email@local");
        assertThat(updatedUser.role().name()).isEqualTo("ADMIN");
        assertThat(passwordEncoder.matches("newpassword456", updatedUser.password())).isTrue();
    }

    @Test
    void testUpdateUserPartialFields() {
        // Register a user first
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var input = new UserInput("Partial Update User", "partial.update@local", "password123", String.valueOf(librarianRole.getId()));
        var user = userService.registerUser(input);

        // Update only name
        var updateInput = new com.demandline.library.service.model.input.UserUpdateInput(
            user.id(),
            "New Name Only",
            null,
            null,
            null
        );

        var updatedUser = userService.updateUser(updateInput);

        assertThat(updatedUser.name()).isEqualTo("New Name Only");
        assertThat(updatedUser.email()).isEqualTo("partial.update@local");
        assertThat(updatedUser.role().name()).isEqualTo("LIBRARIAN");
    }

    @Test
    void testUpdateUserWithInvalidUserId() {
        var updateInput = new com.demandline.library.service.model.input.UserUpdateInput(
            999999,
            "Invalid User",
            null,
            null,
            null
        );

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> userService.updateUser(updateInput)
        ).getMessage()).isEqualTo("User not found");
    }

    @Test
    void testUpdateUserWithInvalidRole() {
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var input = new UserInput("Role Update Test", "role.update@local", "password123", String.valueOf(librarianRole.getId()));
        var user = userService.registerUser(input);

        var updateInput = new com.demandline.library.service.model.input.UserUpdateInput(
            user.id(),
            null,
            null,
            null,
            "999"
        );

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> userService.updateUser(updateInput)
        ).getMessage()).isEqualTo("Role not found");
    }

    @Test
    void testDeleteUser() {
        var adminRole = roleRepository.findByName("ADMIN").get();
        var input = new UserInput("Delete Test User", "delete.test@local", "password123", String.valueOf(adminRole.getId()));
        var user = userService.registerUser(input);

        assertThat(user.isActive()).isTrue();

        userService.deleteUser(String.valueOf(user.id()));

        // User should still exist but be inactive
        var deletedUser = userRepository.findById(user.id());
        assertThat(deletedUser).isPresent();
        assertThat(deletedUser.get().getActive()).isFalse();

        // Should not appear in getUserByEmail (assuming it filters active users)
        var fromService = userService.getUserByEmail("delete.test@local");
        assertThat(fromService).isPresent();
        assertThat(fromService.get().isActive()).isFalse();
    }

    @Test
    void testDeleteUserWithInvalidId() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> userService.deleteUser("999999")
        ).getMessage()).isEqualTo("User not found");
    }

    @Test
    void testGetAllUsersWithoutMembers() {
        // Create users with different roles
        var adminRole = roleRepository.findByName("ADMIN").get();
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var memberRole = roleRepository.findByName("MEMBER").get();

        userService.registerUser(new UserInput("Admin 1", "admin1@local", "pass", String.valueOf(adminRole.getId())));
        userService.registerUser(new UserInput("Librarian 1", "lib1@local", "pass", String.valueOf(librarianRole.getId())));
        userService.registerUser(new UserInput("Member 1", "member1@local", "pass", String.valueOf(memberRole.getId())));

        var users = userService.getAllUsers(false, 100, 0);

        // Should not include members
        assertThat(users).isNotEmpty();
        assertThat(users.stream().noneMatch(u -> "MEMBER".equalsIgnoreCase(u.role().name()))).isTrue();
    }

    @Test
    void testGetAllUsersWithMembers() {
        var adminRole = roleRepository.findByName("ADMIN").get();
        var memberRole = roleRepository.findByName("MEMBER").get();

        userService.registerUser(new UserInput("Admin 2", "admin2@local", "pass", String.valueOf(adminRole.getId())));
        userService.registerUser(new UserInput("Member 2", "member2@local", "pass", String.valueOf(memberRole.getId())));

        var users = userService.getAllUsers(true, 100, 0);

        // Should include members
        assertThat(users).isNotEmpty();
        assertThat(users.stream().anyMatch(u -> "MEMBER".equalsIgnoreCase(u.role().name()))).isTrue();
    }

    @Test
    void testGetAllUsersWithPagination() {
        var adminRole = roleRepository.findByName("ADMIN").get();

        // Create multiple users
        for (int i = 0; i < 5; i++) {
            userService.registerUser(new UserInput("User " + i, "pagination" + i + "@local", "pass", String.valueOf(adminRole.getId())));
        }

        // Get first page
        var page1 = userService.getAllUsers(true, 2, 0);
        assertThat(page1).hasSize(2);

        // Get second page
        var page2 = userService.getAllUsers(true, 2, 2);
        assertThat(page2).hasSize(2);

        // Verify different results
        assertThat(page1.get(0).id()).isNotEqualTo(page2.get(0).id());
    }

    @Test
    void testGetUserByEmailNotFound() {
        var user = userService.getUserByEmail("nonexistent@local");
        assertThat(user).isEmpty();
    }

    @Test
    void testGetUserByEmailWithDifferentRoles() {
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var frontDeskRole = roleRepository.findByName("FRONT_DESK_STAFF").get();

        userService.registerUser(new UserInput("Librarian", "librarian.test@local", "pass", String.valueOf(librarianRole.getId())));
        userService.registerUser(new UserInput("Front Desk", "frontdesk.test@local", "pass", String.valueOf(frontDeskRole.getId())));

        var librarian = userService.getUserByEmail("librarian.test@local");
        var frontDesk = userService.getUserByEmail("frontdesk.test@local");

        assertThat(librarian).isPresent();
        assertThat(librarian.get().role().name()).isEqualTo("LIBRARIAN");

        assertThat(frontDesk).isPresent();
        assertThat(frontDesk.get().role().name()).isEqualTo("FRONT_DESK_STAFF");
    }

    @Test
    void testMigrationDataExists() {
        // Verify that all expected roles were created by migration
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(roleRepository.findByName("LIBRARIAN")).isPresent();
        assertThat(roleRepository.findByName("FRONT_DESK_STAFF")).isPresent();
        assertThat(roleRepository.findByName("MEMBER")).isPresent();

        // Verify default users from migration exist
        assertThat(userRepository.findByEmail("admin@library.local")).isPresent();
        assertThat(userRepository.findByEmail("librarian@library.local")).isPresent();
        assertThat(userRepository.findByEmail("frontdesk@library.local")).isPresent();
    }

    @Test
    void testRolePermissions() {
        var adminRole = roleRepository.findByName("ADMIN").get();
        var librarianRole = roleRepository.findByName("LIBRARIAN").get();
        var frontDeskRole = roleRepository.findByName("FRONT_DESK_STAFF").get();
        var memberRole = roleRepository.findByName("MEMBER").get();

        // Verify permissions are stored
        assertThat(adminRole.getPermissions()).isNotNull();
        assertThat(adminRole.getPermissions()).contains("ADMIN:CREATE");
        assertThat(adminRole.getPermissions()).contains("BOOK:READ");

        assertThat(librarianRole.getPermissions()).isNotNull();
        assertThat(librarianRole.getPermissions()).contains("BOOK:CREATE");

        assertThat(frontDeskRole.getPermissions()).isNotNull();
        assertThat(frontDeskRole.getPermissions()).contains("BORROW:READ");

        assertThat(memberRole.getPermissions()).isNotNull();
        assertThat(memberRole.getPermissions()).contains("MEMBER:READ");
    }
}

