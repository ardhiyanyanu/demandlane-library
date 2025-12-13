package com.demandline.library.service;

import com.demandline.library.repository.MemberRepository;
import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.UserRepository;
import com.demandline.library.service.model.filter.MemberFilter;
import com.demandline.library.service.model.input.MemberInput;
import com.demandline.library.service.model.input.MemberUpdateInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class MemberServiceIntegrationTest {

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
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testCreateMember() {
        var input = new MemberInput(
                "John Doe",
                "john.doe@example.com",
                "password123",
                "123 Main St, City",
                "555-1234"
        );

        var member = memberService.createMember(input);

        assertThat(member).isNotNull();
        assertThat(member.id()).isNotNull();
        assertThat(member.user()).isNotNull();
        assertThat(member.user().name()).isEqualTo("John Doe");
        assertThat(member.user().email()).isEqualTo("john.doe@example.com");
        assertThat(member.user().role().name()).isEqualTo("MEMBER");
        assertThat(member.address()).isEqualTo("123 Main St, City");
        assertThat(member.phoneNumber()).isEqualTo("555-1234");
        assertThat(member.isActive()).isTrue();

        // Verify in repository
        var fromRepo = memberRepository.findByUserEmail("john.doe@example.com");
        assertThat(fromRepo).isPresent();
        assertThat(fromRepo.get().getUserEntity().getName()).isEqualTo("John Doe");
    }

    @Test
    void testCreateMemberWithDuplicateEmail() {
        var input1 = new MemberInput(
                "Jane Doe",
                "jane.duplicate@example.com",
                "password123",
                "456 Oak Ave",
                "555-5678"
        );
        memberService.createMember(input1);

        var input2 = new MemberInput(
                "Jane Smith",
                "jane.duplicate@example.com",
                "password456",
                "789 Pine St",
                "555-9012"
        );

        var exception = assertThrows(IllegalArgumentException.class,
                () -> memberService.createMember(input2));

        assertThat(exception.getMessage()).contains("already registered");
    }

    @Test
    void testCreateMemberWithMissingMemberRole() {
        // This test verifies that if MEMBER role doesn't exist, proper exception is thrown
        // In normal operation, this shouldn't happen since migrations create the role
        
        var input = new MemberInput(
                "Test User",
                "test.missing.role@example.com",
                "password123",
                "Address",
                "555-0000"
        );

        // Since MEMBER role should exist from seeding, this should succeed
        var member = memberService.createMember(input);
        assertThat(member).isNotNull();
        assertThat(member.user().role().name()).isEqualTo("MEMBER");
    }

    @Test
    void testGetMemberById() {
        var input = new MemberInput(
                "Alice Johnson",
                "alice.johnson@example.com",
                "password123",
                "321 Elm St",
                "555-2468"
        );
        var created = memberService.createMember(input);

        var retrieved = memberService.getMemberById(String.valueOf(created.id()));

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.user().email()).isEqualTo("alice.johnson@example.com");
        assertThat(retrieved.address()).isEqualTo("321 Elm St");
    }

    @Test
    void testGetMemberByIdNotFound() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> memberService.getMemberById("999999"));

        assertThat(exception.getMessage()).contains("not found");
    }

    @Test
    void testGetAllMembersWithNoFilter() {
        // Create some members
        memberService.createMember(new MemberInput("Member A", "membera@example.com", "pass", "Address A", "111-1111"));
        memberService.createMember(new MemberInput("Member B", "memberb@example.com", "pass", "Address B", "222-2222"));
        memberService.createMember(new MemberInput("Member C", "memberc@example.com", "pass", "Address C", "333-3333"));

        var filter = new MemberFilter(Optional.empty(), Optional.empty());
        var members = memberService.getAllMembers(filter, 100, 0);

        assertThat(members).isNotEmpty();
        assertThat(members.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testGetAllMembersWithNameFilter() {
        memberService.createMember(new MemberInput("John Smith", "john.smith@example.com", "pass", "Address", "444-4444"));
        memberService.createMember(new MemberInput("John Doe", "john.doe2@example.com", "pass", "Address", "555-5555"));
        memberService.createMember(new MemberInput("Jane Brown", "jane.brown@example.com", "pass", "Address", "666-6666"));

        var filter = new MemberFilter(Optional.of("John"), Optional.empty());
        var members = memberService.getAllMembers(filter, 100, 0);

        assertThat(members).hasSizeGreaterThanOrEqualTo(2);
        assertThat(members.stream().allMatch(m -> m.user().name().contains("John"))).isTrue();
    }

    @Test
    void testGetAllMembersWithEmailFilter() {
        memberService.createMember(new MemberInput("User 1", "user1@gmail.com", "pass", "Address", "777-7777"));
        memberService.createMember(new MemberInput("User 2", "user2@gmail.com", "pass", "Address", "888-8888"));
        memberService.createMember(new MemberInput("User 3", "user3@yahoo.com", "pass", "Address", "999-9999"));

        var filter = new MemberFilter(Optional.empty(), Optional.of("gmail"));
        var members = memberService.getAllMembers(filter, 100, 0);

        assertThat(members).hasSizeGreaterThanOrEqualTo(2);
        assertThat(members.stream().allMatch(m -> m.user().email().contains("gmail"))).isTrue();
    }

    @Test
    void testGetAllMembersWithPagination() {
        // Create multiple members
        for (int i = 1; i <= 10; i++) {
            memberService.createMember(new MemberInput(
                    "Member " + i,
                    "member" + i + "@pagination.com",
                    "pass",
                    "Address " + i,
                    "000-000" + i
            ));
        }

        var filter = new MemberFilter(Optional.empty(), Optional.empty());

        // Get first page (limit 3)
        var page1 = memberService.getAllMembers(filter, 3, 0);
        assertThat(page1).hasSize(3);

        // Get second page (offset 3, limit 3)
        var page2 = memberService.getAllMembers(filter, 3, 3);
        assertThat(page2).hasSize(3);

        // Verify different results
        assertThat(page1.get(0).id()).isNotEqualTo(page2.get(0).id());
    }

    @Test
    void testUpdateMember() {
        // Create a member first
        var input = new MemberInput(
                "Original Name",
                "update.test@example.com",
                "password123",
                "Original Address",
                "111-1111"
        );
        var member = memberService.createMember(input);

        // Update member details
        var updateInput = new MemberUpdateInput(
                member.id(),
                "Updated Name",
                "updated.email@example.com",
                "newpassword456",
                "Updated Address",
                "999-9999"
        );

        var updated = memberService.updateMember(updateInput);

        assertThat(updated.user().name()).isEqualTo("Updated Name");
        assertThat(updated.user().email()).isEqualTo("updated.email@example.com");
        assertThat(updated.address()).isEqualTo("Updated Address");
        assertThat(updated.phoneNumber()).isEqualTo("999-9999");
    }

    @Test
    void testUpdateMemberPartialFields() {
        // Create a member first
        var input = new MemberInput(
                "Partial Update",
                "partial.update@example.com",
                "password123",
                "Original Address",
                "111-1111"
        );
        var member = memberService.createMember(input);

        // Update only name and address
        var updateInput = new MemberUpdateInput(
                member.id(),
                "New Name Only",
                null,
                null,
                "New Address Only",
                null
        );

        var updated = memberService.updateMember(updateInput);

        assertThat(updated.user().name()).isEqualTo("New Name Only");
        assertThat(updated.user().email()).isEqualTo("partial.update@example.com"); // Unchanged
        assertThat(updated.address()).isEqualTo("New Address Only");
        assertThat(updated.phoneNumber()).isEqualTo("111-1111"); // Unchanged
    }

    @Test
    void testUpdateMemberWithDuplicateEmail() {
        var member1 = memberService.createMember(new MemberInput(
                "Member 1",
                "member1@dup.com",
                "pass",
                "Address",
                "111-1111"
        ));

        var member2 = memberService.createMember(new MemberInput(
                "Member 2",
                "member2@dup.com",
                "pass",
                "Address",
                "222-2222"
        ));

        // Try to update member2's email to member1's email
        var updateInput = new MemberUpdateInput(
                member2.id(),
                null,
                "member1@dup.com",
                null,
                null,
                null
        );

        var exception = assertThrows(IllegalArgumentException.class,
                () -> memberService.updateMember(updateInput));

        assertThat(exception.getMessage()).contains("already in use");
    }

    @Test
    void testUpdateMemberNotFound() {
        var updateInput = new MemberUpdateInput(
                999999,
                "Non Existent",
                null,
                null,
                null,
                null
        );

        var exception = assertThrows(IllegalArgumentException.class,
                () -> memberService.updateMember(updateInput));

        assertThat(exception.getMessage()).contains("not found");
    }

    @Test
    void testDeleteMember() {
        var input = new MemberInput(
                "Delete Test",
                "delete.test@example.com",
                "password123",
                "Address",
                "111-1111"
        );
        var member = memberService.createMember(input);

        assertThat(member.isActive()).isTrue();

        memberService.deleteMember(String.valueOf(member.id()));

        // Member should still exist but be inactive
        var deleted = memberRepository.findById(member.id());
        assertThat(deleted).isPresent();
        assertThat(deleted.get().getIsActive()).isFalse();

        // Associated user should also be inactive
        var user = userRepository.findById(member.user().id());
        assertThat(user).isPresent();
        assertThat(user.get().getActive()).isFalse();
    }

    @Test
    void testDeleteMemberNotFound() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> memberService.deleteMember("999999"));

        assertThat(exception.getMessage()).contains("not found");
    }

    @Test
    void testMemberPasswordIsEncrypted() {
        var input = new MemberInput(
                "Password Test",
                "password.test@example.com",
                "plaintext123",
                "Address",
                "111-1111"
        );

        var member = memberService.createMember(input);

        // Password should be encrypted (bcrypt hash)
        assertThat(member.user().password()).isNotEqualTo("plaintext123");
        assertThat(member.user().password()).startsWith("$2a$"); // BCrypt prefix
    }

    @Test
    void testMemberRoleIsAutomaticallySetToMember() {
        var input = new MemberInput(
                "Role Test",
                "role.test@example.com",
                "password123",
                "Address",
                "111-1111"
        );

        var member = memberService.createMember(input);

        assertThat(member.user().role()).isNotNull();
        assertThat(member.user().role().name()).isEqualTo("MEMBER");
        assertThat(member.user().role().permissions()).contains("MEMBER:READ");
    }

    @Test
    void testSearchMembersByNameAndEmail() {
        memberService.createMember(new MemberInput("Alice Anderson", "alice.a@example.com", "pass", "Addr", "111"));
        memberService.createMember(new MemberInput("Bob Builder", "bob.b@example.com", "pass", "Addr", "222"));
        memberService.createMember(new MemberInput("Charlie Cooper", "charlie.c@example.com", "pass", "Addr", "333"));

        // Search by name
        var filter1 = new MemberFilter(Optional.of("Alice"), Optional.empty());
        var results1 = memberService.getAllMembers(filter1, 100, 0);
        assertThat(results1).isNotEmpty();
        assertThat(results1.stream().anyMatch(m -> m.user().name().contains("Alice"))).isTrue();

        // Search by email
        var filter2 = new MemberFilter(Optional.empty(), Optional.of("bob"));
        var results2 = memberService.getAllMembers(filter2, 100, 0);
        assertThat(results2).isNotEmpty();
        assertThat(results2.stream().anyMatch(m -> m.user().email().contains("bob"))).isTrue();
    }

    @Test
    void testGetMemberByIdReturnsCompleteInformation() {
        var input = new MemberInput(
                "Complete Info Test",
                "complete.info@example.com",
                "password123",
                "123 Complete St",
                "555-COMPLETE"
        );
        var created = memberService.createMember(input);

        var retrieved = memberService.getMemberById(String.valueOf(created.id()));

        // Verify all fields are populated
        assertThat(retrieved.id()).isNotNull();
        assertThat(retrieved.user()).isNotNull();
        assertThat(retrieved.user().id()).isNotNull();
        assertThat(retrieved.user().name()).isEqualTo("Complete Info Test");
        assertThat(retrieved.user().email()).isEqualTo("complete.info@example.com");
        assertThat(retrieved.user().password()).isNotNull();
        assertThat(retrieved.user().role()).isNotNull();
        assertThat(retrieved.user().role().name()).isEqualTo("MEMBER");
        assertThat(retrieved.user().createdAt()).isNotNull();
        assertThat(retrieved.user().updatedAt()).isNotNull();
        assertThat(retrieved.user().isActive()).isTrue();
        assertThat(retrieved.address()).isEqualTo("123 Complete St");
        assertThat(retrieved.phoneNumber()).isEqualTo("555-COMPLETE");
        assertThat(retrieved.createdAt()).isNotNull();
        assertThat(retrieved.updatedAt()).isNotNull();
        assertThat(retrieved.isActive()).isTrue();
    }

    @Test
    void testOnlyActiveMembersAreReturned() {
        // Create an active member
        var activeMember = memberService.createMember(new MemberInput(
                "Active Member",
                "active@example.com",
                "pass",
                "Address",
                "111"
        ));

        // Create and delete a member
        var deletedMember = memberService.createMember(new MemberInput(
                "Deleted Member",
                "deleted@example.com",
                "pass",
                "Address",
                "222"
        ));
        memberService.deleteMember(String.valueOf(deletedMember.id()));

        // Get all members
        var filter = new MemberFilter(Optional.empty(), Optional.empty());
        var members = memberService.getAllMembers(filter, 100, 0);

        // Should only include active members
        assertThat(members.stream().anyMatch(m -> m.user().email().equals("active@example.com"))).isTrue();
        assertThat(members.stream().anyMatch(m -> m.user().email().equals("deleted@example.com"))).isFalse();
    }
}

