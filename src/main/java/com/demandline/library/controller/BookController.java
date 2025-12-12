package com.demandline.library.controller;

import com.demandline.library.service.BookService;
import com.demandline.library.service.model.Book;
import com.demandline.library.service.model.filter.BookFilter;
import com.demandline.library.service.model.input.BookInput;
import com.demandline.library.service.model.input.BookUpdateInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Book Management Controller
 * Handles book inventory management endpoints
 * Requires BOOK related permissions based on operation
 */
@RestController
@RequestMapping("/library/admin/books")
@Tag(name = "Book Management", description = "Book inventory management endpoints (Librarian access)")
@SecurityRequirement(name = "Bearer Authentication")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    @Operation(
        summary = "Add New Book",
        description = "Add a single new book to the inventory with details such as title, author, ISBN, and quantity.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Book added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or ISBN already exists"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BOOK:CREATE required)"),
        @ApiResponse(responseCode = "409", description = "ISBN already exists in system")
    })
    public ResponseEntity<BookCreateResponse> addBook(@RequestBody BookCreateRequest request) {
        var newBook = bookService.createBook(new BookInput(
            request.title(),
            request.author(),
            request.isbn(),
            request.totalCopies()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(new BookCreateResponse(newBook));
    }

    @PostMapping("/csv")
    @Operation(
        summary = "Bulk Insert/Update Books from CSV",
        description = "Bulk insert and update multiple books from a CSV file. CSV format: title,author,isbn,totalCopies",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Books imported successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid CSV format or data"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BOOK:CREATE required)")
    })
    public ResponseEntity<BulkImportResponse> bulkImportBooks(
        @Parameter(description = "CSV file containing book data") @RequestParam("file") MultipartFile file) {
        var results = bookService.createMultipleBook(file);
        return ResponseEntity.ok(new BulkImportResponse(
                results.importedCount(),
                results.updatedCount(),
                results.failedCount(),
                "Bulk import completed"
        ));
    }

    @GetMapping
    @Operation(
        summary = "List All Books",
        description = "Retrieve list of all books in the inventory with their details and availability status.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Books list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BOOK:READ required)")
    })
    public ResponseEntity<List<BookResponse>> listBooks(
        @Parameter(description = "Search term for title") @RequestParam(required = false) Optional<String> bookName,
        @Parameter(description = "Search term for author") @RequestParam(required = false) Optional<String> authorName,
        @Parameter(description = "Search term for isbn") @RequestParam(required = false) Optional<String> isbn,
        @Parameter(description = "Include all borrowed book") @RequestParam(required = false, defaultValue = "true") boolean showNotAvailable,
        @Parameter(description = "Page number for pagination") @RequestParam(defaultValue = "0") Integer page,
        @Parameter(description = "Page size for pagination") @RequestParam(defaultValue = "20") Integer size) {
        var books = bookService.getAllBooks(new BookFilter(
                bookName,
                authorName,
                isbn,
                showNotAvailable
        ), size, page);
        return ResponseEntity.ok(books.stream().map(BookResponse::new).toList());
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update Book Information",
        description = "Update book information such as title, author, quantity, or availability status.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Book information updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BOOK:UPDATE required)"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<BookResponse> updateBook(
        @Parameter(description = "Book ID") @PathVariable Integer id,
        @RequestBody BookUpdateRequest request) {
        var updatedBook = bookService.updateBook(new BookUpdateInput(
            id,
            request.title(),
            request.author(),
            request.isbn(),
            request.totalCopies()
        ));
        return ResponseEntity.ok(new BookResponse(updatedBook));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Remove Book from Inventory",
        description = "Remove a book from the inventory. The book is marked as inactive instead of being deleted.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Book removed successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BOOK:DELETE required)"),
        @ApiResponse(responseCode = "404", description = "Book not found"),
        @ApiResponse(responseCode = "409", description = "Cannot delete book with active loans")
    })
    public ResponseEntity<Void> deleteBook(@Parameter(description = "Book ID") @PathVariable String id) {
        bookService.deleteBook(id);
        return ResponseEntity.ok().build();
    }

    // Request/Response DTOs
    public record BookCreateRequest(
        String title,
        String author,
        String isbn,
        Integer totalCopies
    ) {}

    public record BookUpdateRequest(
        String title,
        String author,
        String isbn,
        Integer totalCopies
    ) {}

    public record BookCreateResponse(
        Integer bookId,
        String isbn,
        String title,
        String author,
        Integer totalCopies,
        Integer availableCopies,
        String message
    ) {
        public BookCreateResponse(Book book) {
            this(book.id(), book.isbn(), book.title(), book.author(), book.totalCopies(), book.totalCopies(), "");
        }
    }

    public record BookResponse(
        Integer id,
        String title,
        String author,
        String isbn,
        Integer totalCopies,
        Integer availableCopies
    ) {
        public BookResponse(Book book) {
            this(book.id(), book.title(), book.author(), book.isbn(), book.totalCopies(), book.availableCopies());
        }
    }

    public record BulkImportResponse(
        Integer importedCount,
        Integer updatedCount,
        Integer failedCount,
        String message
    ) {
        public BulkImportResponse() {
            this(0, 0, 0, "");
        }
    }
}

