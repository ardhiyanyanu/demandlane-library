package com.demandline.library;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Library Management Service
 * Verifies that Flyway migrations are executed successfully
 *
 * This test class uses TestContainers to spin up a PostgreSQL database
 * and verifies that all Flyway migrations are executed correctly.
 */
@Testcontainers
@SpringBootTest
class LibraryApplicationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("library_test_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        assertNotNull(jdbcTemplate, "JdbcTemplate should not be null");
    }

    @Test
    void testMigrationsExecutedSuccessfully() {
        // Verify that the flyway_schema_history table exists and has migrations
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );
        assertNotNull(migrationCount, "Migration count should not be null");
        assertTrue(migrationCount >= 2, "At least 2 migrations should have been executed");
    }

    @Test
    void testAllRolesCreated() {
        // Verify that all 4 roles are created
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles",
                Integer.class
        );
        assertEquals(4, roleCount, "Should have exactly 4 roles (Admin, Librarian, Front Desk Staff, Member)");
    }

    @Test
    void testAdminRoleExists() {
        // Verify ADMIN role exists with correct permissions
        Map<String, Object> adminRole = jdbcTemplate.queryForMap(
                "SELECT id, name, permissions FROM roles WHERE name = 'ADMIN'"
        );
        assertNotNull(adminRole, "ADMIN role should exist");
        assertEquals("ADMIN", adminRole.get("name"), "Role name should be ADMIN");

        // Verify ADMIN has all required permissions
        String permissions = adminRole.get("permissions").toString();
        assertTrue(permissions.contains("ADMIN:CREATE"), "ADMIN should have ADMIN:CREATE permission");
        assertTrue(permissions.contains("ADMIN:READ"), "ADMIN should have ADMIN:READ permission");
        assertTrue(permissions.contains("ADMIN:UPDATE"), "ADMIN should have ADMIN:UPDATE permission");
        assertTrue(permissions.contains("ADMIN:DELETE"), "ADMIN should have ADMIN:DELETE permission");
        assertTrue(permissions.contains("BOOK:CREATE"), "ADMIN should have BOOK:CREATE permission");
        assertTrue(permissions.contains("BOOK:READ"), "ADMIN should have BOOK:READ permission");
        assertTrue(permissions.contains("BOOK:UPDATE"), "ADMIN should have BOOK:UPDATE permission");
        assertTrue(permissions.contains("BOOK:DELETE"), "ADMIN should have BOOK:DELETE permission");
        assertTrue(permissions.contains("BORROW:READ"), "ADMIN should have BORROW:READ permission");
        assertTrue(permissions.contains("BORROW:UPDATE"), "ADMIN should have BORROW:UPDATE permission");
        assertTrue(permissions.contains("BORROW:DELETE"), "ADMIN should have BORROW:DELETE permission");
        assertTrue(permissions.contains("MEMBER:READ"), "ADMIN should have MEMBER:READ permission");
        assertTrue(permissions.contains("MEMBER:UPDATE"), "ADMIN should have MEMBER:UPDATE permission");
    }

    @Test
    void testLibrarianRoleExists() {
        // Verify LIBRARIAN role exists with correct permissions
        Map<String, Object> librarianRole = jdbcTemplate.queryForMap(
                "SELECT id, name, permissions FROM roles WHERE name = 'LIBRARIAN'"
        );
        assertNotNull(librarianRole, "LIBRARIAN role should exist");
        assertEquals("LIBRARIAN", librarianRole.get("name"), "Role name should be LIBRARIAN");

        // Verify LIBRARIAN has book-related permissions only
        String permissions = librarianRole.get("permissions").toString();
        assertTrue(permissions.contains("BOOK:CREATE"), "LIBRARIAN should have BOOK:CREATE permission");
        assertTrue(permissions.contains("BOOK:READ"), "LIBRARIAN should have BOOK:READ permission");
        assertTrue(permissions.contains("BOOK:UPDATE"), "LIBRARIAN should have BOOK:UPDATE permission");
        assertTrue(permissions.contains("BOOK:DELETE"), "LIBRARIAN should have BOOK:DELETE permission");
        assertFalse(permissions.contains("ADMIN:CREATE"), "LIBRARIAN should not have ADMIN permissions");
    }

    @Test
    void testFrontDeskStaffRoleExists() {
        // Verify FRONT_DESK_STAFF role exists with correct permissions
        Map<String, Object> frontDeskRole = jdbcTemplate.queryForMap(
                "SELECT id, name, permissions FROM roles WHERE name = 'FRONT_DESK_STAFF'"
        );
        assertNotNull(frontDeskRole, "FRONT_DESK_STAFF role should exist");
        assertEquals("FRONT_DESK_STAFF", frontDeskRole.get("name"), "Role name should be FRONT_DESK_STAFF");

        // Verify FRONT_DESK_STAFF has borrow and member permissions
        String permissions = frontDeskRole.get("permissions").toString();
        assertTrue(permissions.contains("BORROW:READ"), "FRONT_DESK_STAFF should have BORROW:READ permission");
        assertTrue(permissions.contains("BORROW:UPDATE"), "FRONT_DESK_STAFF should have BORROW:UPDATE permission");
        assertTrue(permissions.contains("BORROW:DELETE"), "FRONT_DESK_STAFF should have BORROW:DELETE permission");
        assertTrue(permissions.contains("MEMBER:READ"), "FRONT_DESK_STAFF should have MEMBER:READ permission");
        assertTrue(permissions.contains("MEMBER:UPDATE"), "FRONT_DESK_STAFF should have MEMBER:UPDATE permission");
    }

    @Test
    void testMemberRoleExists() {
        // Verify MEMBER role exists with read-only permission
        Map<String, Object> memberRole = jdbcTemplate.queryForMap(
                "SELECT id, name, permissions FROM roles WHERE name = 'MEMBER'"
        );
        assertNotNull(memberRole, "MEMBER role should exist");
        assertEquals("MEMBER", memberRole.get("name"), "Role name should be MEMBER");

        // Verify MEMBER has only MEMBER:READ permission
        String permissions = memberRole.get("permissions").toString();
        assertTrue(permissions.contains("MEMBER:READ"), "MEMBER should have MEMBER:READ permission");
    }

    @Test
    void testDefaultUsersCreated() {
        // Verify that all 3 default users are created
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_active = true",
                Integer.class
        );
        assertEquals(3, userCount, "Should have exactly 3 default users (Admin, Librarian, Front Desk Staff)");
    }

    @Test
    void testAdminUserExists() {
        // Verify default admin user exists with correct role
        Map<String, Object> adminUser = jdbcTemplate.queryForMap(
                "SELECT u.id, u.name, u.email, u.is_active, r.name as role_name " +
                "FROM users u JOIN roles r ON u.role_id = r.id WHERE u.email = 'admin@library.local'"
        );
        assertNotNull(adminUser, "Admin user should exist");
        assertEquals("Administrator", adminUser.get("name"), "Admin user name should be 'Administrator'");
        assertEquals("admin@library.local", adminUser.get("email"), "Admin user email should be 'admin@library.local'");
        assertEquals(true, adminUser.get("is_active"), "Admin user should be active");
        assertEquals("ADMIN", adminUser.get("role_name"), "Admin user should have ADMIN role");
    }

    @Test
    void testLibrarianUserExists() {
        // Verify default librarian user exists with correct role
        Map<String, Object> librarianUser = jdbcTemplate.queryForMap(
                "SELECT u.id, u.name, u.email, u.is_active, r.name as role_name " +
                "FROM users u JOIN roles r ON u.role_id = r.id WHERE u.email = 'librarian@library.local'"
        );
        assertNotNull(librarianUser, "Librarian user should exist");
        assertEquals("Sample Librarian", librarianUser.get("name"), "Librarian user name should be 'Sample Librarian'");
        assertEquals("librarian@library.local", librarianUser.get("email"), "Librarian user email should be 'librarian@library.local'");
        assertEquals(true, librarianUser.get("is_active"), "Librarian user should be active");
        assertEquals("LIBRARIAN", librarianUser.get("role_name"), "Librarian user should have LIBRARIAN role");
    }

    @Test
    void testFrontDeskStaffUserExists() {
        // Verify default front desk staff user exists with correct role
        Map<String, Object> frontDeskUser = jdbcTemplate.queryForMap(
                "SELECT u.id, u.name, u.email, u.is_active, r.name as role_name " +
                "FROM users u JOIN roles r ON u.role_id = r.id WHERE u.email = 'frontdesk@library.local'"
        );
        assertNotNull(frontDeskUser, "Front desk staff user should exist");
        assertEquals("Sample Front Desk Staff", frontDeskUser.get("name"), "Front desk user name should be 'Sample Front Desk Staff'");
        assertEquals("frontdesk@library.local", frontDeskUser.get("email"), "Front desk user email should be 'frontdesk@library.local'");
        assertEquals(true, frontDeskUser.get("is_active"), "Front desk user should be active");
        assertEquals("FRONT_DESK_STAFF", frontDeskUser.get("role_name"), "Front desk user should have FRONT_DESK_STAFF role");
    }

    @Test
    void testTablesCreated() {
        // Verify all required tables exist
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class
        );
        assertTrue(tables.contains("roles"), "roles table should exist");
        assertTrue(tables.contains("users"), "users table should exist");
        assertTrue(tables.contains("members"), "members table should exist");
        assertTrue(tables.contains("books"), "books table should exist");
        assertTrue(tables.contains("loans"), "loans table should exist");
    }
}

