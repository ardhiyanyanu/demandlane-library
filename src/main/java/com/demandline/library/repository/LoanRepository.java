package com.demandline.library.repository;

import com.demandline.library.repository.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Loan entity
 * Provides database operations for book loans/borrowings
 */
@Repository
public interface LoanRepository extends JpaRepository<Loan, Integer> {
    
    /**
     * Find all loans for a specific member
     * @param memberId the member id
     * @return List of loans for the member
     */
    List<Loan> findByMemberId(Integer memberId);
    
    /**
     * Find all active (not returned) loans for a member
     * @param memberId the member id
     * @return List of active loans where return_date is null
     */
    @Query("SELECT l FROM Loan l WHERE l.member.id = :memberId AND l.returnDate IS NULL")
    List<Loan> findActiveLoans(@Param("memberId") Integer memberId);
    
    /**
     * Find all loans for a specific book
     * @param bookId the book id
     * @return List of loans for the book
     */
    List<Loan> findByBookId(Integer bookId);
    
    /**
     * Find overdue loans (return_date is null and due_date has passed)
     * @param currentDate the current date/time
     * @return List of overdue loans
     */
    @Query("SELECT l FROM Loan l WHERE l.returnDate IS NULL AND l.dueDate < :currentDate")
    List<Loan> findOverdueLoans(@Param("currentDate") LocalDateTime currentDate);
    
    /**
     * Check if a member has an active loan for a specific book
     * @param memberId the member id
     * @param bookId the book id
     * @return true if member has active loan for this book
     */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Loan l WHERE l.member.id = :memberId AND l.book.id = :bookId AND l.returnDate IS NULL")
    boolean hasActiveLoan(@Param("memberId") Integer memberId, @Param("bookId") Integer bookId);
    
    /**
     * Find a specific active loan by member and book
     * @param memberId the member id
     * @param bookId the book id
     * @return Optional containing the active loan if found
     */
    @Query("SELECT l FROM Loan l WHERE l.member.id = :memberId AND l.book.id = :bookId AND l.returnDate IS NULL")
    Optional<Loan> findActiveLoan(@Param("memberId") Integer memberId, @Param("bookId") Integer bookId);
}

