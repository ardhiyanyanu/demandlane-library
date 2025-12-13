package com.demandline.library.repository;

import com.demandline.library.repository.model.BookEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository interface for Book entity
 * Provides database operations for books in the library inventory
 */
@Repository
public interface BookRepository extends JpaRepository<BookEntity, Integer> {
    
    /**
     * Find a book by ISBN
     * @param isbn the book ISBN
     * @return Optional containing the book if found
     */
    Optional<BookEntity> findByIsbn(String isbn);
    
    /**
     * Find books by title (case-insensitive)
     * @param title the book title
     * @return List of books matching the title
     */
    List<BookEntity> findByTitleIgnoreCase(String title);
    
    /**
     * Find books by author (case-insensitive)
     * @param author the author name
     * @return List of books by the author
     */
    List<BookEntity> findByAuthorIgnoreCase(String author);
    
    /**
     * Find all books with available copies
     * @return List of books with available_copies > 0
     */
    @Query("SELECT b FROM BookEntity b WHERE b.availableCopies > 0")
    List<BookEntity> findAllAvailableBooks();
    
    /**
     * Search books by title or author
     * @param searchTerm the search term
     * @return List of books matching title or author
     */
    @Query("SELECT b FROM BookEntity b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(b.author) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<BookEntity> searchBooks(@Param("searchTerm") String searchTerm);

    /**
     * Find a book by ID with pessimistic write lock
     * Prevents concurrent modifications to book availability
     * @param bookId the book ID
     * @return Optional containing the book if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookEntity b WHERE b.id = :bookId")
    Optional<BookEntity> findByIdWithLock(@Param("bookId") Integer bookId);
}

