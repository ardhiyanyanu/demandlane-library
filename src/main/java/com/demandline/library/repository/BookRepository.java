package com.demandline.library.repository;

import com.demandline.library.repository.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface BookRepository extends JpaRepository<Book, Integer> {
    
    /**
     * Find a book by ISBN
     * @param isbn the book ISBN
     * @return Optional containing the book if found
     */
    Optional<Book> findByIsbn(String isbn);
    
    /**
     * Find books by title (case-insensitive)
     * @param title the book title
     * @return List of books matching the title
     */
    List<Book> findByTitleIgnoreCase(String title);
    
    /**
     * Find books by author (case-insensitive)
     * @param author the author name
     * @return List of books by the author
     */
    List<Book> findByAuthorIgnoreCase(String author);
    
    /**
     * Find all books with available copies
     * @return List of books with available_copies > 0
     */
    @Query("SELECT b FROM Book b WHERE b.availableCopies > 0")
    List<Book> findAllAvailableBooks();
    
    /**
     * Search books by title or author
     * @param searchTerm the search term
     * @return List of books matching title or author
     */
    @Query("SELECT b FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(b.author) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Book> searchBooks(@Param("searchTerm") String searchTerm);
}

