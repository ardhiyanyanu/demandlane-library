package com.demandline.library.service;

import com.demandline.library.repository.BookRepository;
import com.demandline.library.repository.LoanRepository;
import com.demandline.library.repository.model.BookEntity;
import com.demandline.library.service.model.Book;
import com.demandline.library.service.model.BookBulkImportResponse;
import com.demandline.library.service.model.filter.BookFilter;
import com.demandline.library.service.model.input.BookInput;
import com.demandline.library.service.model.input.BookUpdateInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookService {
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    @Transactional
    public Book createBook(BookInput bookInput) {
        // Check if ISBN already exists
        if (bookRepository.findByIsbn(bookInput.isbn()).isPresent()) {
            throw new IllegalArgumentException("Book with ISBN " + bookInput.isbn() + " already exists");
        }

        var bookEntity = BookEntity.builder()
                .title(bookInput.title())
                .author(bookInput.author())
                .isbn(bookInput.isbn())
                .totalCopies(bookInput.totalCopies())
                .availableCopies(bookInput.totalCopies())
                .build();

        var saved = bookRepository.save(bookEntity);
        log.info("Created book: {} (ISBN: {})", saved.getTitle(), saved.getIsbn());
        return mapToBook(saved);
    }

    @Transactional
    public BookBulkImportResponse createMultipleBook(MultipartFile file) {
        int importedCount = 0;
        int updatedCount = 0;
        int failedCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                try {
                    String[] fields = line.split(",");
                    if (fields.length < 4) {
                        log.warn("Invalid CSV line (not enough fields): {}", line);
                        failedCount++;
                        continue;
                    }

                    String title = fields[0].trim();
                    String author = fields[1].trim();
                    String isbn = fields[2].trim();
                    Integer totalCopies = Integer.parseInt(fields[3].trim());

                    // Check if book exists
                    var existingBook = bookRepository.findByIsbn(isbn);
                    if (existingBook.isPresent()) {
                        // Update existing book
                        var book = existingBook.get();
                        book.setTitle(title);
                        book.setAuthor(author);
                        book.setTotalCopies(book.getTotalCopies() + totalCopies);
                        book.setAvailableCopies(book.getAvailableCopies() + totalCopies);
                        bookRepository.save(book);
                        updatedCount++;
                        log.debug("Updated book: {} (ISBN: {})", title, isbn);
                    } else {
                        // Create new book
                        var newBook = BookEntity.builder()
                                .title(title)
                                .author(author)
                                .isbn(isbn)
                                .totalCopies(totalCopies)
                                .availableCopies(totalCopies)
                                .build();
                        bookRepository.save(newBook);
                        importedCount++;
                        log.debug("Imported book: {} (ISBN: {})", title, isbn);
                    }
                } catch (Exception e) {
                    log.error("Failed to process CSV line: {} - Error: {}", line, e.getMessage());
                    failedCount++;
                }
            }

            log.info("Bulk import completed - Imported: {}, Updated: {}, Failed: {}",
                    importedCount, updatedCount, failedCount);

        } catch (Exception e) {
            log.error("Failed to process CSV file", e);
            throw new RuntimeException("Failed to process CSV file: " + e.getMessage());
        }

        return new BookBulkImportResponse(importedCount, updatedCount, failedCount);
    }

    @Transactional
    public Book updateBook(BookUpdateInput updatedBook) {
        var bookEntity = bookRepository.findById(updatedBook.id())
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + updatedBook.id()));

        // Update basic fields
        if (updatedBook.title() != null) {
            bookEntity.setTitle(updatedBook.title());
        }
        if (updatedBook.author() != null) {
            bookEntity.setAuthor(updatedBook.author());
        }
        if (updatedBook.isbn() != null && !updatedBook.isbn().equals(bookEntity.getIsbn())) {
            // Check if new ISBN already exists
            if (bookRepository.findByIsbn(updatedBook.isbn()).isPresent()) {
                throw new IllegalArgumentException("ISBN " + updatedBook.isbn() + " already exists");
            }
            bookEntity.setIsbn(updatedBook.isbn());
        }

        // Handle totalCopies update with validation
        if (updatedBook.totalCopies() != null) {
            int currentTotal = bookEntity.getTotalCopies();
            int currentAvailable = bookEntity.getAvailableCopies();
            int loanedOut = currentTotal - currentAvailable;
            int newTotal = updatedBook.totalCopies();

            // Check if reducing totalCopies below loaned out count
            if (newTotal < loanedOut) {
                throw new IllegalArgumentException(
                    String.format("Cannot reduce total copies to %d. %d copies are currently loaned out.",
                            newTotal, loanedOut));
            }

            // Update copies
            int difference = newTotal - currentTotal;
            bookEntity.setTotalCopies(newTotal);
            bookEntity.setAvailableCopies(currentAvailable + difference);
        }

        var saved = bookRepository.save(bookEntity);
        log.info("Updated book: {} (ID: {})", saved.getTitle(), saved.getId());
        return mapToBook(saved);
    }

    @Transactional
    public void deleteBook(String bookId) {
        Integer id = Integer.valueOf(bookId);
        var bookEntity = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + bookId));

        // Check if book has active loans
        var activeLoans = loanRepository.findByBookId(id).stream()
                .filter(loan -> loan.getReturnDate() == null)
                .toList();

        if (!activeLoans.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Cannot delete book. %d copies are currently loaned out.", activeLoans.size()));
        }

        bookRepository.deleteById(id);
        log.info("Deleted book: {} (ID: {})", bookEntity.getTitle(), bookEntity.getId());
    }

    public List<Book> getAllBooks(BookFilter filter, int limit, int offset) {
        List<BookEntity> bookEntities;

        // Apply filters
        if (filter.bookName().isPresent() || filter.authorName().isPresent() || filter.isbn().isPresent()) {
            // If any filter is present, use search
            if (filter.isbn().isPresent()) {
                bookEntities = bookRepository.findByIsbn(filter.isbn().get())
                        .map(List::of)
                        .orElse(new ArrayList<>());
            } else if (filter.bookName().isPresent()) {
                String searchTerm = filter.bookName().get();
                bookEntities = bookRepository.searchBooks(searchTerm);
            } else if (filter.authorName().isPresent()) {
                bookEntities = bookRepository.findByAuthorIgnoreCase(filter.authorName().get());
            } else {
                bookEntities = bookRepository.findAll();
            }
        } else {
            // No specific filters, get all books
            bookEntities = filter.showNotAvailable()
                    ? bookRepository.findAll()
                    : bookRepository.findAllAvailableBooks();
        }

        // Filter by availability if needed
        if (!filter.showNotAvailable()) {
            bookEntities = bookEntities.stream()
                    .filter(bookEntity -> bookEntity.getAvailableCopies() > 0)
                    .collect(Collectors.toList());
        }

        // Apply pagination
        return bookEntities.stream()
                .skip(offset)
                .limit(limit)
                .map(this::mapToBook)
                .collect(Collectors.toList());
    }

    private Book mapToBook(BookEntity bookEntity) {
        return new Book(
                bookEntity.getId(),
                bookEntity.getTitle(),
                bookEntity.getAuthor(),
                bookEntity.getIsbn(),
                bookEntity.getTotalCopies(),
                bookEntity.getAvailableCopies(),
                bookEntity.getCreatedAt(),
                bookEntity.getUpdatedAt()
        );
    }
}
