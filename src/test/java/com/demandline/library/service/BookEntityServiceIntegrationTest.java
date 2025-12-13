package com.demandline.library.service;

import com.demandline.library.repository.BookRepository;
import com.demandline.library.repository.LoanRepository;
import com.demandline.library.repository.MemberRepository;
import com.demandline.library.repository.model.BookEntity;
import com.demandline.library.repository.model.LoanEntity;
import com.demandline.library.service.model.BookBulkImportResponse;
import com.demandline.library.service.model.filter.BookFilter;
import com.demandline.library.service.model.input.BookInput;
import com.demandline.library.service.model.input.BookUpdateInput;
import com.demandline.library.service.model.input.MemberInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class BookEntityServiceIntegrationTest {

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
    private BookService bookService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void testCreateBook() {
        var input = new BookInput("Test Book", "Test Author", "ISBN-TEST-001", 5);
        var bookEntity = bookService.createBook(input);

        assertThat(bookEntity).isNotNull();
        assertThat(bookEntity.id()).isNotNull();
        assertThat(bookEntity.title()).isEqualTo("Test Book");
        assertThat(bookEntity.author()).isEqualTo("Test Author");
        assertThat(bookEntity.isbn()).isEqualTo("ISBN-TEST-001");
        assertThat(bookEntity.totalCopies()).isEqualTo(5);
        assertThat(bookEntity.availableCopies()).isEqualTo(5);

        // Verify in repository
        var fromRepo = bookRepository.findByIsbn("ISBN-TEST-001");
        assertThat(fromRepo).isPresent();
        assertThat(fromRepo.get().getTitle()).isEqualTo("Test Book");
    }

    @Test
    void testCreateBookWithDuplicateIsbn() {
        var input1 = new BookInput("Book 1", "Author 1", "ISBN-DUP-001", 3);
        bookService.createBook(input1);

        var input2 = new BookInput("Book 2", "Author 2", "ISBN-DUP-001", 5);
        
        var exception = assertThrows(IllegalArgumentException.class, 
            () -> bookService.createBook(input2));
        
        assertThat(exception.getMessage()).contains("already exists");
    }

    @Test
    void testUpdateBook() {
        // Create a bookEntity first
        var input = new BookInput("Original Title", "Original Author", "ISBN-UPDATE-001", 5);
        var bookEntity = bookService.createBook(input);

        // Update the bookEntity
        var updateInput = new BookUpdateInput(
            bookEntity.id(),
            "Updated Title",
            "Updated Author",
            null,
            10
        );

        var updated = bookService.updateBook(updateInput);

        assertThat(updated.title()).isEqualTo("Updated Title");
        assertThat(updated.author()).isEqualTo("Updated Author");
        assertThat(updated.totalCopies()).isEqualTo(10);
        assertThat(updated.availableCopies()).isEqualTo(10);
    }

    @Test
    void testUpdateBookPartialFields() {
        // Create a bookEntity first
        var input = new BookInput("Partial Update Book", "Author", "ISBN-PARTIAL-001", 5);
        var bookEntity = bookService.createBook(input);

        // Update only title
        var updateInput = new BookUpdateInput(
            bookEntity.id(),
            "New Title Only",
            null,
            null,
            null
        );

        var updated = bookService.updateBook(updateInput);

        assertThat(updated.title()).isEqualTo("New Title Only");
        assertThat(updated.author()).isEqualTo("Author"); // Unchanged
        assertThat(updated.isbn()).isEqualTo("ISBN-PARTIAL-001"); // Unchanged
        assertThat(updated.totalCopies()).isEqualTo(5); // Unchanged
    }

    @Test
    void testUpdateBookWithDuplicateIsbn() {
        var input1 = new BookInput("Book 1", "Author 1", "ISBN-EXISTS-001", 3);
        var book1 = bookService.createBook(input1);

        var input2 = new BookInput("Book 2", "Author 2", "ISBN-EXISTS-002", 5);
        var book2 = bookService.createBook(input2);

        // Try to update book2's ISBN to book1's ISBN
        var updateInput = new BookUpdateInput(
            book2.id(),
            null,
            null,
            "ISBN-EXISTS-001",
            null
        );

        var exception = assertThrows(IllegalArgumentException.class,
            () -> bookService.updateBook(updateInput));
        
        assertThat(exception.getMessage()).contains("already exists");
    }

    @Test
    void testUpdateBookCannotReduceBelowLoanedCopies() {
        // Create a bookEntity
        var input = new BookInput("Loaned Book", "Author", "ISBN-LOAN-001", 5);
        var book = bookService.createBook(input);

        // Create a member
        var memberInput = new MemberInput("Test Member", "testmember@local", "password123", "Test Address", "123456789");
        var member = memberService.createMember(memberInput);
        var memberEntity = memberRepository.findById(member.id()).get();

        // Create loans (simulate 3 books loaned out)
        var bookEntity = bookRepository.findById(book.id()).get();
        bookEntity.setAvailableCopies(2); // 3 loaned out
        bookRepository.save(bookEntity);

        for (int i = 0; i < 3; i++) {
            var loan = LoanEntity.builder()
                    .bookEntity(bookEntity)
                    .memberEntity(memberEntity)
                    .borrowDate(LocalDateTime.now())
                    .dueDate(LocalDateTime.now().plusDays(14))
                    .build();
            loanRepository.save(loan);
        }

        // Try to update totalCopies to 2 (less than 3 loaned out)
        var updateInput = new BookUpdateInput(
            bookEntity.getId(),
            null,
            null,
            null,
            2
        );

        var exception = assertThrows(IllegalArgumentException.class,
            () -> bookService.updateBook(updateInput));
        
        assertThat(exception.getMessage()).contains("loaned out");
    }

    @Test
    void testDeleteBook() {
        var input = new BookInput("Delete Book", "Author", "ISBN-DELETE-001", 3);
        var bookEntity = bookService.createBook(input);

        bookService.deleteBook(String.valueOf(bookEntity.id()));

        // Verify bookEntity is deleted
        var deleted = bookRepository.findById(bookEntity.id());
        assertThat(deleted).isEmpty();
    }

    @Test
    void testDeleteBookWithActiveLoans() {
        // Create a bookEntity
        var input = new BookInput("Book With Loans", "Author", "ISBN-LOAN-DEL-001", 3);
        var book = bookService.createBook(input);

        // Create a member
        var memberInput = new MemberInput("Test Member", "testmember2@local", "password123", "Test Address", "987654321");
        var member = memberService.createMember(memberInput);
        var memberEntity = memberRepository.findById(member.id()).get();

        // Create an active loan
        var bookEntity = bookRepository.findById(book.id()).get();
        var loan = LoanEntity.builder()
                .bookEntity(bookEntity)
                .memberEntity(memberEntity)
                .borrowDate(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(14))
                .build();
        loanRepository.save(loan);

        // Try to delete the bookEntity
        var exception = assertThrows(IllegalArgumentException.class,
            () -> bookService.deleteBook(String.valueOf(bookEntity.getId())));
        
        assertThat(exception.getMessage()).contains("loaned out");
    }

    @Test
    void testGetAllBooksWithNoFilter() {
        // Create some books
        bookService.createBook(new BookInput("Book A", "Author A", "ISBN-A-001", 5));
        bookService.createBook(new BookInput("Book B", "Author B", "ISBN-B-001", 3));
        bookService.createBook(new BookInput("Book C", "Author C", "ISBN-C-001", 0)); // No available copies

        var filter = new BookFilter(Optional.empty(), Optional.empty(), Optional.empty(), false);
        var books = bookService.getAllBooks(filter, 100, 0);

        // Should only return books with available copies
        assertThat(books).isNotEmpty();
        assertThat(books.stream().allMatch(b -> b.availableCopies() > 0)).isTrue();
    }

    @Test
    void testGetAllBooksShowNotAvailable() {
        // Create some books
        bookService.createBook(new BookInput("Available Book", "Author", "ISBN-AVAIL-001", 5));
        
        var notAvailableBook = bookRepository.save(BookEntity.builder()
                .title("Not Available Book")
                .author("Author")
                .isbn("ISBN-NOTAVAIL-001")
                .totalCopies(5)
                .availableCopies(0)
                .build());

        var filter = new BookFilter(Optional.empty(), Optional.empty(), Optional.empty(), true);
        var books = bookService.getAllBooks(filter, 100, 0);

        assertThat(books).isNotEmpty();
        assertThat(books.stream().anyMatch(b -> b.availableCopies() == 0)).isTrue();
    }

    @Test
    void testGetAllBooksFilterByBookName() {
        bookService.createBook(new BookInput("Java Programming", "Author A", "ISBN-JAVA-001", 5));
        bookService.createBook(new BookInput("Python Programming", "Author B", "ISBN-PYTHON-001", 3));
        bookService.createBook(new BookInput("JavaScript Guide", "Author C", "ISBN-JS-001", 4));

        var filter = new BookFilter(Optional.of("Java"), Optional.empty(), Optional.empty(), false);
        var books = bookService.getAllBooks(filter, 100, 0);

        assertThat(books).hasSize(2); // Java Programming and JavaScript Guide
        assertThat(books.stream().allMatch(b -> 
            b.title().toLowerCase().contains("java"))).isTrue();
    }

    @Test
    void testGetAllBooksFilterByAuthor() {
        bookService.createBook(new BookInput("Book 1", "John Doe", "ISBN-JOHN-001", 5));
        bookService.createBook(new BookInput("Book 2", "John Doe", "ISBN-JOHN-002", 3));
        bookService.createBook(new BookInput("Book 3", "Jane Smith", "ISBN-JANE-001", 4));

        var filter = new BookFilter(Optional.empty(), Optional.of("John Doe"), Optional.empty(), false);
        var books = bookService.getAllBooks(filter, 100, 0);

        assertThat(books).hasSize(2);
        assertThat(books.stream().allMatch(b -> b.author().equals("John Doe"))).isTrue();
    }

    @Test
    void testGetAllBooksFilterByIsbn() {
        bookService.createBook(new BookInput("Book 1", "Author", "ISBN-SPECIFIC-001", 5));
        bookService.createBook(new BookInput("Book 2", "Author", "ISBN-SPECIFIC-002", 3));

        var filter = new BookFilter(Optional.empty(), Optional.empty(), Optional.of("ISBN-SPECIFIC-001"), false);
        var books = bookService.getAllBooks(filter, 100, 0);

        assertThat(books).hasSize(1);
        assertThat(books.get(0).isbn()).isEqualTo("ISBN-SPECIFIC-001");
    }

    @Test
    void testGetAllBooksWithPagination() {
        // Create multiple books
        for (int i = 1; i <= 10; i++) {
            bookService.createBook(new BookInput("Book " + i, "Author", "ISBN-PAGE-" + i, 5));
        }

        // Get first page (limit 3)
        var filter = new BookFilter(Optional.empty(), Optional.empty(), Optional.empty(), false);
        var page1 = bookService.getAllBooks(filter, 3, 0);
        assertThat(page1).hasSize(3);

        // Get second page (offset 3, limit 3)
        var page2 = bookService.getAllBooks(filter, 3, 3);
        assertThat(page2).hasSize(3);

        // Verify different results
        assertThat(page1.get(0).id()).isNotEqualTo(page2.get(0).id());
    }

    @Test
    void testBulkImportBooksFromCsv() {
        String csvContent = """
                Title,Author,ISBN,TotalCopies
                Book Import 1,Import Author 1,ISBN-IMPORT-001,5
                Book Import 2,Import Author 2,ISBN-IMPORT-002,3
                Book Import 3,Import Author 3,ISBN-IMPORT-003,7
                """;

        var file = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                csvContent.getBytes()
        );

        BookBulkImportResponse response = bookService.createMultipleBook(file);

        assertThat(response.importedCount()).isEqualTo(3);
        assertThat(response.updatedCount()).isEqualTo(0);
        assertThat(response.failedCount()).isEqualTo(0);

        // Verify books are created
        assertThat(bookRepository.findByIsbn("ISBN-IMPORT-001")).isPresent();
        assertThat(bookRepository.findByIsbn("ISBN-IMPORT-002")).isPresent();
        assertThat(bookRepository.findByIsbn("ISBN-IMPORT-003")).isPresent();
    }

    @Test
    void testBulkImportUpdatesExistingBooks() {
        // Create an existing bookEntity
        bookService.createBook(new BookInput("Existing Book", "Author", "ISBN-EXISTING-001", 5));

        String csvContent = """
                Title,Author,ISBN,TotalCopies
                Updated Existing Book,Updated Author,ISBN-EXISTING-001,3
                New Book,New Author,ISBN-NEW-001,4
                """;

        var file = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                csvContent.getBytes()
        );

        BookBulkImportResponse response = bookService.createMultipleBook(file);

        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(0);

        // Verify existing bookEntity is updated (copies added)
        var updated = bookRepository.findByIsbn("ISBN-EXISTING-001").get();
        assertThat(updated.getTitle()).isEqualTo("Updated Existing Book");
        assertThat(updated.getTotalCopies()).isEqualTo(8); // 5 + 3
        assertThat(updated.getAvailableCopies()).isEqualTo(8);
    }

    @Test
    void testBulkImportHandlesInvalidLines() {
        String csvContent = """
                Title,Author,ISBN,TotalCopies
                Valid Book,Author,ISBN-VALID-001,5
                Invalid Line,Author
                Another Valid Book,Author 2,ISBN-VALID-002,3
                """;

        var file = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                csvContent.getBytes()
        );

        BookBulkImportResponse response = bookService.createMultipleBook(file);

        assertThat(response.importedCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(1);
    }

    @Test
    void testUpdateBookNotFound() {
        var updateInput = new BookUpdateInput(
            999999,
            "Non Existent",
            null,
            null,
            null
        );

        var exception = assertThrows(IllegalArgumentException.class,
            () -> bookService.updateBook(updateInput));
        
        assertThat(exception.getMessage()).contains("not found");
    }

    @Test
    void testDeleteBookNotFound() {
        var exception = assertThrows(IllegalArgumentException.class,
            () -> bookService.deleteBook("999999"));
        
        assertThat(exception.getMessage()).contains("not found");
    }
}

