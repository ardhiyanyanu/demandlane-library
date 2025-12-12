package com.demandline.library.integration;

import com.demandline.library.controller.AuthController;
import com.demandline.library.controller.StaffController;
import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
public class StaffIntegrationTest {
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testAdminLoginAndCreateLibrarianStaff() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("admin@library.local"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        assertThat(adminToken).isNotEmpty();

        // Step 2: Admin creates a new Librarian staff
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "New Librarian",
                "newlibrarian@library.local",
                "librarian456",
                String.valueOf(librarianRole.getId())
        );

        var createResult = mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newlibrarian@library.local"))
                .andExpect(jsonPath("$.name").value("New Librarian"))
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.staffId").isNumber())
                .andReturn();

        // Step 3: New librarian can login
        var newLibrarianLogin = new AuthController.LoginRequest("newlibrarian@library.local", "librarian456");

        mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLibrarianLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("newlibrarian@library.local"))
                .andExpect(jsonPath("$.role").value("LIBRARIAN"));
    }

    @Test
    void testAdminCreateFrontDeskStaffAndVerifyLogin() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Admin creates Front Desk Staff
        var frontDeskRole = roleRepository.findByName("FRONT_DESK_STAFF").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "New Front Desk",
                "newfrontdesk@library.local",
                "frontdesk456",
                String.valueOf(frontDeskRole.getId())
        );

        mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FRONT_DESK_STAFF"));

        // Step 3: New front desk staff can login
        var newFrontDeskLogin = new AuthController.LoginRequest("newfrontdesk@library.local", "frontdesk456");

        mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newFrontDeskLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("FRONT_DESK_STAFF"));
    }

    @Test
    void testLibrarianCannotCreateStaff() throws Exception {
        // Step 1: Librarian login (from migration data)
        var loginRequest = new AuthController.LoginRequest("librarian@library.local", "librarian123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String librarianToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Librarian tries to create staff (should fail - no ADMIN:CREATE permission)
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "Unauthorized Staff",
                "unauthorized@library.local",
                "password123",
                String.valueOf(librarianRole.getId())
        );

        mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + librarianToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminCanListAllStaff() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Admin lists all staff
        mockMvc.perform(get("/library/admin/staff")
                .header("Authorization", "Bearer " + adminToken)
                .param("limit", "100")
                .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].role").exists());
    }

    @Test
    void testAdminCanUpdateStaff() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Create a staff member
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "Update Test Staff",
                "updatetest@library.local",
                "password123",
                String.valueOf(librarianRole.getId())
        );

        var createResult = mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Integer staffId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("staffId").asInt();

        // Step 3: Update the staff member
        var frontDeskRole = roleRepository.findByName("FRONT_DESK_STAFF").orElseThrow();
        var updateRequest = new StaffController.StaffUpdateRequest(
                "Updated Name",
                "updatetest@library.local",
                null,
                String.valueOf(frontDeskRole.getId())
        );

        mockMvc.perform(put("/library/admin/staff/" + staffId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.role").value("FRONT_DESK_STAFF"));
    }

    @Test
    void testAdminCanDeactivateStaff() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Create a staff member
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "Delete Test Staff",
                "deletetest@library.local",
                "password123",
                String.valueOf(librarianRole.getId())
        );

        var createResult = mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Integer staffId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("staffId").asInt();

        // Step 3: Deactivate the staff member
        mockMvc.perform(delete("/library/admin/staff/" + staffId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Step 4: Verify staff is deactivated (cannot login)
        var deletedStaffLogin = new AuthController.LoginRequest("deletetest@library.local", "password123");

        mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deletedStaffLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testFrontDeskCannotAccessAdminEndpoints() throws Exception {
        // Step 1: Front Desk login
        var loginRequest = new AuthController.LoginRequest("frontdesk@library.local", "frontdesk123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String frontDeskToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Front Desk tries to list staff (should fail - no ADMIN:READ permission)
        mockMvc.perform(get("/library/admin/staff")
                .header("Authorization", "Bearer " + frontDeskToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthorizedAccessWithoutToken() throws Exception {
        // Try to access admin endpoint without token
        mockMvc.perform(get("/library/admin/staff"))
                .andExpect(status().isUnauthorized());

        // Try to create staff without token
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "Unauthorized",
                "unauthorized@library.local",
                "password123",
                String.valueOf(librarianRole.getId())
        );

        mockMvc.perform(post("/library/admin/staff/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidLoginCredentials() throws Exception {
        // Invalid email
        var invalidEmailLogin = new AuthController.LoginRequest("nonexistent@library.local", "password123");

        mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmailLogin)))
                .andExpect(status().isUnauthorized());

        // Invalid password
        var invalidPasswordLogin = new AuthController.LoginRequest("admin@library.local", "wrongpassword");

        mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPasswordLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateStaffWithDuplicateEmail() throws Exception {
        // Step 1: Admin login
        var loginRequest = new AuthController.LoginRequest("admin@library.local", "admin123");

        var loginResult = mockMvc.perform(post("/library/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Try to create staff with existing email (should fail or handle gracefully)
        var librarianRole = roleRepository.findByName("LIBRARIAN").orElseThrow();
        var createStaffRequest = new StaffController.StaffCreateRequest(
                "Duplicate Email Test",
                "librarian@library.local", // Already exists from migration
                "password123",
                String.valueOf(librarianRole.getId())
        );

        // This should fail with conflict or bad request
        mockMvc.perform(post("/library/admin/staff/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createStaffRequest)))
                .andExpect(status().is4xxClientError());
    }
}
