package com.demandline.library.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * Centralized metrics service for library application
 * Manages all application metrics in one place
 */
@Service
public class MetricsService {
    
    // Book metrics
    private final Counter booksCreatedCounter;
    private final Counter booksUpdatedCounter;
    private final Counter booksDeletedCounter;
    
    // Loan metrics
    private final Counter booksLoanedCounter;
    private final Counter booksReturnedCounter;
    private final Counter loanSuccessCounter;
    private final Counter loanFailureCounter;
    private final Counter returnSuccessCounter;
    private final Counter returnFailureCounter;
    private final Timer loanOperationTimer;
    private final Timer returnOperationTimer;
    
    // User metrics
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter userRegistrationCounter;
    
    public MetricsService(MeterRegistry meterRegistry) {
        // Initialize book metrics
        this.booksCreatedCounter = Counter.builder("library.books.created")
                .description("Total number of books created")
                .tag("service", "book")
                .register(meterRegistry);
        
        this.booksUpdatedCounter = Counter.builder("library.books.updated")
                .description("Total number of books updated")
                .tag("service", "book")
                .register(meterRegistry);
        
        this.booksDeletedCounter = Counter.builder("library.books.deleted")
                .description("Total number of books deleted")
                .tag("service", "book")
                .register(meterRegistry);
        
        // Initialize loan metrics
        this.booksLoanedCounter = Counter.builder("library.books.loaned")
                .description("Total number of books loaned")
                .tag("service", "loan")
                .register(meterRegistry);
        
        this.booksReturnedCounter = Counter.builder("library.books.returned")
                .description("Total number of books returned")
                .tag("service", "loan")
                .register(meterRegistry);
        
        this.loanSuccessCounter = Counter.builder("library.loan.requests")
                .description("Total loan requests")
                .tag("service", "loan")
                .tag("status", "success")
                .register(meterRegistry);
        
        this.loanFailureCounter = Counter.builder("library.loan.requests")
                .description("Total loan requests")
                .tag("service", "loan")
                .tag("status", "failure")
                .register(meterRegistry);
        
        this.returnSuccessCounter = Counter.builder("library.return.requests")
                .description("Total return requests")
                .tag("service", "loan")
                .tag("status", "success")
                .register(meterRegistry);
        
        this.returnFailureCounter = Counter.builder("library.return.requests")
                .description("Total return requests")
                .tag("service", "loan")
                .tag("status", "failure")
                .register(meterRegistry);
        
        this.loanOperationTimer = Timer.builder("library.loan.duration")
                .description("Duration of loan operations")
                .tag("service", "loan")
                .register(meterRegistry);
        
        this.returnOperationTimer = Timer.builder("library.return.duration")
                .description("Duration of return operations")
                .tag("service", "loan")
                .register(meterRegistry);
        
        // Initialize user metrics
        this.loginSuccessCounter = Counter.builder("library.login.attempts")
                .description("Total login attempts")
                .tag("service", "user")
                .tag("status", "success")
                .register(meterRegistry);
        
        this.loginFailureCounter = Counter.builder("library.login.attempts")
                .description("Total login attempts")
                .tag("service", "user")
                .tag("status", "failure")
                .register(meterRegistry);
        
        this.userRegistrationCounter = Counter.builder("library.users.registered")
                .description("Total users registered")
                .tag("service", "user")
                .register(meterRegistry);
    }
    
    // Book metrics methods
    public void incrementBooksCreated() {
        booksCreatedCounter.increment();
    }
    
    public void incrementBooksUpdated() {
        booksUpdatedCounter.increment();
    }
    
    public void incrementBooksDeleted() {
        booksDeletedCounter.increment();
    }
    
    // Loan metrics methods
    public void incrementBooksLoaned(int count) {
        booksLoanedCounter.increment(count);
    }
    
    public void incrementBooksReturned(int count) {
        booksReturnedCounter.increment(count);
    }
    
    public void incrementLoanSuccess() {
        loanSuccessCounter.increment();
    }
    
    public void incrementLoanFailure() {
        loanFailureCounter.increment();
    }
    
    public void incrementReturnSuccess() {
        returnSuccessCounter.increment();
    }
    
    public void incrementReturnFailure() {
        returnFailureCounter.increment();
    }
    
    public Timer getLoanOperationTimer() {
        return loanOperationTimer;
    }
    
    public Timer getReturnOperationTimer() {
        return returnOperationTimer;
    }
    
    // User metrics methods
    public void incrementLoginSuccess() {
        loginSuccessCounter.increment();
    }
    
    public void incrementLoginFailure() {
        loginFailureCounter.increment();
    }
    
    public void incrementUserRegistration() {
        userRegistrationCounter.increment();
    }
}

