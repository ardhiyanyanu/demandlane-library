package com.demandline.library.controller;

import com.demandline.library.service.BookService;
import com.demandline.library.service.model.Book;
import com.demandline.library.service.model.filter.BookFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Public Books Search Controller
 * Handles public book search endpoints (no authentication required)
 * Allows anyone to search and view available books in the library
 */
@RestController
@RequestMapping("/library/public/books")
@Tag(name = "Public Search", description = "Public book search endpoints (No authentication required)")
public class PublicSearchController {
    private final BookService bookService;

    public PublicSearchController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(
        summary = "Search and View Available Books",
        description = "Search for available books in the library by title, author, or ISBN. " +
                      "Returns only books with available_copies > 0. No authentication required.",
        security = {} // Empty security requirement means no authentication needed
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Available books list retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public ResponseEntity<List<PublicBookResponse>> searchAvailableBooks(
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
        return ResponseEntity.ok(books.stream().map(PublicBookResponse::new).toList());
    }

    // Response DTO
    public record PublicBookResponse(
        Integer id,
        String title,
        String author,
        String isbn,
        Integer availableCopies,
        Integer totalCopies
    ) {
        public PublicBookResponse(Book book) {
            this(
                book.id(),
                book.title(),
                book.author(),
                book.isbn(),
                book.availableCopies(),
                book.totalCopies()
            );
        }
    }
}

