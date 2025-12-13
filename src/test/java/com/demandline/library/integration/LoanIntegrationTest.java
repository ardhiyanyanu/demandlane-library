package com.demandline.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class LoanIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    public static RedisContainer redis = new RedisContainer("redis:8-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Set max books per member for testing
        registry.add("library.maxBooksPerMember", () -> "3");
        registry.add("library.loanPeriodDays", () -> "14");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String librarianToken;
    private String frontdeskToken;
    private Integer member1Id;
    private Integer member2Id;
    private Integer book1Id;
    private Integer book2Id;
    private Integer book3Id;
    private Integer book4Id;
    private Integer csvBook1Id;
    private Integer csvBook2Id;

    @BeforeEach
    void setUp() throws Exception {
        // Reset IDs
        member1Id = null;
        member2Id = null;
        book1Id = null;
        book2Id = null;
        book3Id = null;
        book4Id = null;
        csvBook1Id = null;
        csvBook2Id = null;
    }

    @Test
    void testCompleteLibraryLoanWorkflow() throws Exception {
        // Step 1: Librarian login
        librarianToken = loginAs("librarian@library.local", "librarian123");

        // Step 2: Librarian adds a new book using single endpoint
        book1Id = addBookViaEndpoint(librarianToken, "The Great Gatsby", "F. Scott Fitzgerald", "ISBN-001", 5);
        book2Id = addBookViaEndpoint(librarianToken, "1984", "George Orwell", "ISBN-002", 3);
        book3Id = addBookViaEndpoint(librarianToken, "To Kill a Mockingbird", "Harper Lee", "ISBN-003", 4);
        book4Id = addBookViaEndpoint(librarianToken, "Pride and Prejudice", "Jane Austen", "ISBN-004", 2);

        // Step 3: Librarian uploads CSV with more books
        uploadBooksViaCsv(librarianToken);

        // Get CSV book IDs
        csvBook1Id = getBookIdByIsbn(librarianToken, "ISBN-CSV-001");
        csvBook2Id = getBookIdByIsbn(librarianToken, "ISBN-CSV-002");

        // Step 4: Front desk staff login
        frontdeskToken = loginAs("frontdesk@library.local", "frontdesk123");

        // Step 5: Front desk registers new member
        member1Id = registerMember(frontdeskToken, "John Doe", "john.doe@test.com", "password123", "123 Main St", "555-1001");

        // Step 6: Member1 tries to loan books exceeding maximum (4 books when max is 3)
        tryLoanBooksExpectError(member1Id, List.of(book1Id, book2Id, book3Id, book4Id),
                "Cannot loan more than 3 books at once");

        // Step 7: Member1 loans exactly the maximum number of books (3 books)
        List<Integer> member1LoanIds = loanBooksSuccessfully(member1Id, List.of(book1Id, book2Id, book3Id));

        // Step 8: Member1 tries to loan again while having active loans
        tryLoanBooksExpectError(member1Id, List.of(book4Id),
                "Member has active loans and cannot borrow more books");

        // Step 9: Register second member
        member2Id = registerMember(frontdeskToken, "Jane Smith", "jane.smith@test.com", "password456", "456 Oak Ave", "555-1002");

        // Step 10: Set a book to have 0 available copies and member2 tries to loan it
        // First, we need to update book to have only 1 copy
        updateBookCopies(librarianToken, csvBook1Id, 1);

        // Member2 loans the last copy
        List<Integer> member2LoanIds = loanBooksSuccessfully(member2Id, List.of(csvBook1Id));

        // Step 11: Member1 returns some books (return 2 books, keep 1)
        returnBooksSuccessfully(member1Id, member1LoanIds.subList(0, 2), List.of(book1Id, book2Id));

        // Step 12: Member1 tries to loan a new book while still having 1 active loan
        // This should error since no active loans should prevent new loans
        tryLoanBooksExpectError(member1Id, List.of(book4Id),
                "Member has active loans and cannot borrow more books");

        // Step 13: Member2 borrows book that was already returned by member1
        returnBooksSuccessfully(member2Id, member2LoanIds, List.of(csvBook1Id));
        loanBooksSuccessfully(member2Id, List.of(book1Id));

        // Step 14: Member1 tries to return an already returned book
        tryReturnBooksExpectError(member1Id, member1LoanIds.get(0), book1Id, "already returned");

        System.out.println("✅ All integration test scenarios passed successfully!");
    }

    // Helper methods

    private String loginAs(String email, String password) throws Exception {
        String loginRequest = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password
        ));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        return (String) responseMap.get("token");
    }

    private Integer addBookViaEndpoint(String token, String title, String author, String isbn, int totalCopies) throws Exception {
        String bookRequest = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "author", author,
                "isbn", isbn,
                "totalCopies", totalCopies
        ));

        String response = mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        return (Integer) responseMap.get("bookId");
    }

    private void uploadBooksViaCsv(String token) throws Exception {
        String csvContent = """
                title,author,isbn,totalCopies
                The Catcher in the Rye,J.D. Salinger,ISBN-CSV-001,3
                Brave New World,Aldous Huxley,ISBN-CSV-002,4
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                csvContent.getBytes()
        );

        mockMvc.perform(multipart("/api/books/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount", greaterThan(0)));
    }

    private Integer getBookIdByIsbn(String token, String isbn) throws Exception {
        String response = mockMvc.perform(get("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .param("isbn", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> books = objectMapper.readValue(response, List.class);
        return (Integer) books.get(0).get("id");
    }

    private Integer registerMember(String token, String name, String email, String password,
                                   String address, String phoneNumber) throws Exception {
        String memberRequest = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", email,
                "password", password,
                "address", address,
                "phoneNumber", phoneNumber
        ));

        String response = mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        return (Integer) responseMap.get("memberId");
    }

    private void tryLoanBooksExpectError(Integer memberId, List<Integer> bookIds, String expectedErrorMessage) throws Exception {
        String loanRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "bookIds", bookIds,
                "requestId", "test-loan-error-" + System.currentTimeMillis()
        ));

        mockMvc.perform(post("/api/loans/borrow")
                        .header("Authorization", "Bearer " + frontdeskToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequest))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(containsString(expectedErrorMessage)));
    }

    private List<Integer> loanBooksSuccessfully(Integer memberId, List<Integer> bookIds) throws Exception {
        String loanRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "bookIds", bookIds,
                "requestId", "test-loan-" + System.currentTimeMillis()
        ));

        String response = mockMvc.perform(post("/api/loans/borrow")
                        .header("Authorization", "Bearer " + frontdeskToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookLoan", hasSize(bookIds.size())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        List<Map<String, Object>> bookLoans = (List<Map<String, Object>>) responseMap.get("bookLoan");
        return bookLoans.stream()
                .map(bookLoan -> (Integer) bookLoan.get("loanId"))
                .toList();
    }

    private void updateBookCopies(String token, Integer bookId, int totalCopies) throws Exception {
        String updateRequest = objectMapper.writeValueAsString(Map.of(
                "id", bookId,
                "totalCopies", totalCopies
        ));

        mockMvc.perform(put("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andDo(print())
                .andExpect(status().isOk());
    }

    private void returnBooksSuccessfully(Integer memberId, List<Integer> loanIds, List<Integer> bookIds) throws Exception {
        List<Map<String, Integer>> returnPairs = new java.util.ArrayList<>();
        for (int i = 0; i < loanIds.size(); i++) {
            returnPairs.add(Map.of(
                    "loanId", loanIds.get(i),
                    "bookId", bookIds.get(i)
            ));
        }

        String returnRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "returnRequests", returnPairs,
                "requestId", "test-return-" + System.currentTimeMillis()
        ));

        mockMvc.perform(post("/api/loans/return")
                        .header("Authorization", "Bearer " + frontdeskToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookReturn", hasSize(loanIds.size())))
                .andExpect(jsonPath("$.bookReturn[*].returnDate", everyItem(notNullValue())));
    }

    private void tryReturnBooksExpectError(Integer memberId, Integer loanId, Integer bookId,
                                          String expectedErrorMessage) throws Exception {
        String returnRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "returnRequests", List.of(Map.of(
                        "loanId", loanId,
                        "bookId", bookId
                )),
                "requestId", "test-return-error-" + System.currentTimeMillis()
        ));

        mockMvc.perform(post("/api/loans/return")
                        .header("Authorization", "Bearer " + frontdeskToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnRequest))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(containsString(expectedErrorMessage)));
    }
}
