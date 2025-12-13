package com.demandline.library.service;

import com.demandline.library.config.LibraryConfiguration;
import com.demandline.library.repository.BookRepository;
import com.demandline.library.repository.LoanRepository;
import com.demandline.library.repository.MemberRepository;
import com.demandline.library.repository.model.BookEntity;
import com.demandline.library.repository.model.LoanEntity;
import com.demandline.library.repository.model.MemberEntity;
import com.demandline.library.service.model.Book;
import com.demandline.library.service.model.Loan;
import com.demandline.library.service.model.LoanBook;
import com.demandline.library.service.model.LoanBookMember;
import com.demandline.library.service.model.Member;
import com.demandline.library.service.model.filter.LoanFilter;
import com.demandline.library.service.model.input.LoanInput;
import com.demandline.library.service.model.input.ReturnInput;
import com.demandline.library.observability.MetricsService;
import com.demandline.library.service.util.RedisLockUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class LoanService {
    private static final long REDIS_LOAN_TTL_SECONDS = 3600; // 1 hour
    private static final String LOAN_REQUEST_PREFIX = "loan:request:";
    private static final String RETURN_REQUEST_PREFIX = "return:request:";
    private static final long LOCK_WAIT_TIMEOUT_SECONDS = 30;

    private final BookService bookService;
    private final MemberService memberService;
    private final LibraryConfiguration libraryConfiguration;
    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisLockUtil redisLockUtil;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public LoanService(BookService bookService,
                       MemberService memberService,
                       LibraryConfiguration libraryConfiguration,
                       LoanRepository loanRepository,
                       BookRepository bookRepository,
                       MemberRepository memberRepository,
                       RedisTemplate<String, String> redisTemplate,
                       RedisLockUtil redisLockUtil,
                       ObjectMapper objectMapper,
                       MetricsService metricsService) {
        this.bookService = bookService;
        this.memberService = memberService;
        this.libraryConfiguration = libraryConfiguration;
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.redisTemplate = redisTemplate;
        this.redisLockUtil = redisLockUtil;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    /**
     * Loan books to a member
     * Uses distributed locking to prevent race conditions
     * @param loanInput contains member ID and list of book IDs to loan
     * @return Loan record with member and loaned books
     */
    @Transactional
    public Loan loanBooks(LoanInput loanInput) {
        return metricsService.getLoanOperationTimer().record(() -> {
            String lockKey = "member:" + loanInput.memberId();
            String lockValue = UUID.randomUUID().toString();

            // Try to acquire lock, wait if another process is using it
            if (!redisLockUtil.acquireLock(lockKey, lockValue)) {
                // Wait for the lock to be released
                if (!redisLockUtil.waitForLock(lockKey, LOCK_WAIT_TIMEOUT_SECONDS)) {
                    metricsService.incrementLoanFailure();
                    throw new IllegalStateException("Timeout waiting for loan lock to be released");
                }
                // Try to acquire lock again after waiting
                if (!redisLockUtil.acquireLock(lockKey, lockValue)) {
                    metricsService.incrementLoanFailure();
                    throw new IllegalStateException("Failed to acquire loan lock after waiting");
                }
            }

            try {
                // Fetch member
                MemberEntity memberEntity = memberRepository.findById(loanInput.memberId())
                        .orElseThrow(() -> {
                            metricsService.incrementLoanFailure();
                            return new IllegalArgumentException("Member not found");
                        });

                // Validate and process each book
                List<LoanEntity> loanEntities = loanInput.bookIds().stream()
                        .map(bookId -> {
                            // Check if member already has active loan for this book
                            if (loanRepository.hasActiveLoan(loanInput.memberId(), bookId)) {
                                metricsService.incrementLoanFailure();
                                throw new IllegalArgumentException("Member already has active loan for book ID: " + bookId);
                            }

                            // Fetch book with pessimistic lock to prevent race conditions
                            BookEntity bookEntity = bookRepository.findByIdWithLock(bookId)
                                    .orElseThrow(() -> {
                                        metricsService.incrementLoanFailure();
                                        return new IllegalArgumentException("Book not found: " + bookId);
                                    });

                            // Check availability (prevent race condition with lock)
                            if (bookEntity.getAvailableCopies() <= 0) {
                                metricsService.incrementLoanFailure();
                                throw new IllegalArgumentException("Book not available: " + bookEntity.getTitle());
                            }

                            // Decrease available copies
                            bookEntity.setAvailableCopies(bookEntity.getAvailableCopies() - 1);
                            bookRepository.save(bookEntity);

                            // Create loan record
                            LocalDateTime now = LocalDateTime.now();
                            LocalDateTime dueDate = now.plusDays(libraryConfiguration.getLoanPeriodDays());

                            return LoanEntity.builder()
                                    .memberEntity(memberEntity)
                                    .bookEntity(bookEntity)
                                    .borrowDate(now)
                                    .dueDate(dueDate)
                                    .build();
                        })
                        .collect(Collectors.toList());

                // Save all loans
                loanRepository.saveAll(loanEntities);

                // Track metrics
                metricsService.incrementBooksLoaned(loanEntities.size());
                metricsService.incrementLoanSuccess();

                // Build and return response
                Member member = new Member(
                        memberEntity.getId(),
                        null, // User will be populated separately if needed
                        memberEntity.getAddress(),
                        memberEntity.getPhoneNumber(),
                        memberEntity.getCreatedAt(),
                        memberEntity.getUpdatedAt(),
                        memberEntity.getIsActive()
                );

                List<LoanBook> loanBooks = loanEntities.stream()
                        .map(loan -> new LoanBook(
                                loan.getId(),
                                mapEntityToBook(loan.getBookEntity()),
                                loan.getBorrowDate(),
                                loan.getReturnDate(),
                                loan.getDueDate(),
                                loan.getCreatedAt(),
                                loan.getUpdatedAt()
                        ))
                        .collect(Collectors.toList());

                return new Loan(member, loanBooks);
            } catch (RuntimeException e) {
                metricsService.incrementLoanFailure();
                throw e;
            } finally {
                // Always release the lock
                redisLockUtil.releaseLock(lockKey, lockValue);
            }
        });
    }

    /**
     * Retrieve loan result from cache by request ID
     * If request is still processing, wait for completion
     * @param requestId the request ID to retrieve
     * @return Cached Loan result
     */
    public Loan getLoanByRequestId(String requestId) {
        String redisKey = LOAN_REQUEST_PREFIX + requestId;
        String lockKey = "request:lock:" + requestId;

        // Check if request is still being processed
        if (redisLockUtil.lockExists(lockKey)) {
            // Wait for processing to complete
            if (!redisLockUtil.waitForLock(lockKey, LOCK_WAIT_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("Timeout waiting for loan request to complete");
            }
        }

        // Try to get the cached result
        String cachedResult = redisTemplate.opsForValue().get(redisKey);
        if (cachedResult == null) {
            throw new IllegalArgumentException("Loan request not found or has expired: " + requestId);
        }

        try {
            return objectMapper.readValue(cachedResult, Loan.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize loan result", e);
        }
    }

    /**
     * Return books by a member
     * Uses distributed locking to prevent race conditions
     * @param returnInput contains member ID and list of loans with books to return
     * @return Loan record with returned books
     */
    @Transactional
    public Loan returnBooks(ReturnInput returnInput) {
        return metricsService.getReturnOperationTimer().record(() -> {
            String lockKey = "member:" + returnInput.memberId();
            String lockValue = UUID.randomUUID().toString();

            // Try to acquire lock, wait if another process is using it
            if (!redisLockUtil.acquireLock(lockKey, lockValue)) {
                // Wait for the lock to be released
                if (!redisLockUtil.waitForLock(lockKey, LOCK_WAIT_TIMEOUT_SECONDS)) {
                    metricsService.incrementReturnFailure();
                    throw new IllegalStateException("Timeout waiting for return lock to be released");
                }
                // Try to acquire lock again after waiting
                if (!redisLockUtil.acquireLock(lockKey, lockValue)) {
                    metricsService.incrementReturnFailure();
                    throw new IllegalStateException("Failed to acquire return lock after waiting");
                }
            }

            try {
                // Fetch member
                MemberEntity memberEntity = memberRepository.findById(returnInput.memberId())
                        .orElseThrow(() -> {
                            metricsService.incrementReturnFailure();
                            return new IllegalArgumentException("Member not found");
                        });

                LocalDateTime returnDate = LocalDateTime.now();
                List<LoanEntity> returnedLoans = returnInput.returnPairInputs().stream()
                        .map(returnPair -> {
                            // Fetch loan
                            LoanEntity loan = loanRepository.findById(returnPair.loanId())
                                    .orElseThrow(() -> {
                                        metricsService.incrementReturnFailure();
                                        return new IllegalArgumentException("Loan not found: " + returnPair.loanId());
                                    });

                            // Verify loan belongs to member
                            if (!loan.getMemberEntity().getId().equals(returnInput.memberId())) {
                                metricsService.incrementReturnFailure();
                                throw new IllegalArgumentException("Loan does not belong to this member");
                            }

                            // Check if already returned
                            if (loan.getReturnDate() != null) {
                                metricsService.incrementReturnFailure();
                                throw new IllegalArgumentException("Loan already returned: " + returnPair.loanId());
                            }

                            // Fetch book to restore available copies
                            BookEntity bookEntity = loan.getBookEntity();

                            // Increase available copies
                            bookEntity.setAvailableCopies(bookEntity.getAvailableCopies() + 1);
                            bookRepository.save(bookEntity);

                            // Update loan with return date
                            loan.setReturnDate(returnDate);
                            return loanRepository.save(loan);
                        })
                        .collect(Collectors.toList());

                // Track metrics
                metricsService.incrementBooksReturned(returnedLoans.size());
                metricsService.incrementReturnSuccess();

                // Build and return response
                Member member = new Member(
                        memberEntity.getId(),
                        null, // User will be populated separately if needed
                        memberEntity.getAddress(),
                        memberEntity.getPhoneNumber(),
                        memberEntity.getCreatedAt(),
                        memberEntity.getUpdatedAt(),
                        memberEntity.getIsActive()
                );

                List<LoanBook> loanBooks = returnedLoans.stream()
                        .map(loan -> new LoanBook(
                                loan.getId(),
                                mapEntityToBook(loan.getBookEntity()),
                                loan.getBorrowDate(),
                                loan.getReturnDate(),
                                loan.getDueDate(),
                                loan.getCreatedAt(),
                                loan.getUpdatedAt()
                        ))
                        .collect(Collectors.toList());

                return new Loan(member, loanBooks);
            } catch (RuntimeException e) {
                metricsService.incrementReturnFailure();
                throw e;
            } finally {
                // Always release the lock
                redisLockUtil.releaseLock(lockKey, lockValue);
            }
        });
    }

    /**
     * Retrieve return result from cache by request ID
     * If request is still processing, wait for completion
     * @param requestId the request ID to retrieve
     * @return Cached Loan result
     */
    public Loan getReturnByRequestId(String requestId) {
        String redisKey = RETURN_REQUEST_PREFIX + requestId;
        String lockKey = "return:request:lock:" + requestId;

        // Check if request is still being processed
        if (redisLockUtil.lockExists(lockKey)) {
            // Wait for processing to complete
            if (!redisLockUtil.waitForLock(lockKey, LOCK_WAIT_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("Timeout waiting for return request to complete");
            }
        }

        // Try to get the cached result
        String cachedResult = redisTemplate.opsForValue().get(redisKey);
        if (cachedResult == null) {
            throw new IllegalArgumentException("Return request not found or has expired: " + requestId);
        }

        try {
            return objectMapper.readValue(cachedResult, Loan.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize return result", e);
        }
    }

    /**
     * Get all loans for a member
     * @param memberId the member ID
     * @param filter loan filter criteria
     * @return List of loans for the member
     */
    public List<LoanBook> getLoansByMemberId(Integer memberId, LoanFilter filter) {
        List<LoanEntity> loanEntities;

        if (filter.onlyActiveLoans()) {
            loanEntities = loanRepository.findActiveLoans(memberId);
        } else {
            loanEntities = loanRepository.findByMemberId(memberId);
        }

        return loanEntities.stream()
                .map(loan -> new LoanBook(
                        loan.getId(),
                        mapEntityToBook(loan.getBookEntity()),
                        loan.getBorrowDate(),
                        loan.getReturnDate(),
                        loan.getDueDate(),
                        loan.getCreatedAt(),
                        loan.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get all loans in the system
     * @param filter loan filter criteria
     * @return List of all loans with member and book information
     */
    public List<LoanBookMember> getAllLoans(LoanFilter filter) {
        List<LoanEntity> loanEntities;

        if (filter.onlyActiveLoans()) {
            // Get all loans and filter for active ones
            loanEntities = loanRepository.findAll().stream()
                    .filter(l -> l.getReturnDate() == null)
                    .collect(Collectors.toList());
        } else if (filter.onlyOverdueLoans()) {
            loanEntities = loanRepository.findOverdueLoans(LocalDateTime.now());
        } else {
            loanEntities = loanRepository.findAll();
        }

        return loanEntities.stream()
                .map(this::mapEntityToLoanBookMember)
                .collect(Collectors.toList());
    }

    /**
     * Get all loans for a specific book
     * @param bookId the book ID
     * @param filter loan filter criteria
     * @return List of loans for the book
     */
    public List<LoanBookMember> getLoansByBook(Integer bookId, LoanFilter filter) {
        List<LoanEntity> loanEntities = loanRepository.findByBookId(bookId);

        if (filter.onlyActiveLoans()) {
            loanEntities = loanEntities.stream()
                    .filter(l -> l.getReturnDate() == null)
                    .collect(Collectors.toList());
        } else if (filter.onlyOverdueLoans()) {
            LocalDateTime now = LocalDateTime.now();
            loanEntities = loanEntities.stream()
                    .filter(l -> l.getReturnDate() == null && l.getDueDate().isBefore(now))
                    .collect(Collectors.toList());
        }

        return loanEntities.stream()
                .map(this::mapEntityToLoanBookMember)
                .collect(Collectors.toList());
    }

    /**
     * Store loan request result in Redis cache
     * Used after processing loanBooks request
     * @param requestId the request ID
     * @param loan the loan result
     */
    public void cacheLoanResult(String requestId, Loan loan) {
        try {
            String redisKey = LOAN_REQUEST_PREFIX + requestId;
            String loanJson = objectMapper.writeValueAsString(loan);
            redisTemplate.opsForValue().set(redisKey, loanJson, REDIS_LOAN_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cache loan result", e);
        }
    }

    /**
     * Store return request result in Redis cache
     * Used after processing returnBooks request
     * @param requestId the request ID
     * @param loan the return result
     */
    public void cacheReturnResult(String requestId, Loan loan) {
        try {
            String redisKey = RETURN_REQUEST_PREFIX + requestId;
            String loanJson = objectMapper.writeValueAsString(loan);
            redisTemplate.opsForValue().set(redisKey, loanJson, REDIS_LOAN_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cache return result", e);
        }
    }

    // Helper methods

    /**
     * Map LoanEntity to LoanBookMember DTO
     */
    private LoanBookMember mapEntityToLoanBookMember(LoanEntity loanEntity) {
        MemberEntity memberEntity = loanEntity.getMemberEntity();
        return new LoanBookMember(
                loanEntity.getId(),
                new Member(
                        memberEntity.getId(),
                        null,
                        memberEntity.getAddress(),
                        memberEntity.getPhoneNumber(),
                        memberEntity.getCreatedAt(),
                        memberEntity.getUpdatedAt(),
                        memberEntity.getIsActive()
                ),
                mapEntityToBook(loanEntity.getBookEntity()),
                loanEntity.getBorrowDate(),
                loanEntity.getReturnDate(),
                loanEntity.getDueDate(),
                loanEntity.getCreatedAt(),
                loanEntity.getUpdatedAt()
        );
    }

    /**
     * Map BookEntity to Book DTO
     */
    private Book mapEntityToBook(BookEntity bookEntity) {
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
