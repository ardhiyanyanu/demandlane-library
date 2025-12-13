package com.demandline.library.service;

import com.demandline.library.repository.BookRepository;
import com.demandline.library.repository.LoanRepository;
import com.demandline.library.repository.MemberRepository;
import com.demandline.library.repository.model.BookEntity;
import com.demandline.library.repository.model.MemberEntity;
import com.demandline.library.service.model.filter.LoanFilter;
import com.demandline.library.service.model.input.LoanInput;
import com.demandline.library.service.model.input.MemberInput;
import com.demandline.library.service.model.input.ReturnInput;
import com.demandline.library.service.model.input.ReturnPairInput;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class LoanServiceIntegrationTest {

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
    }

    @Autowired
    private LoanService loanService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private MemberRepository memberRepository;

    private MemberEntity testMember;
    private BookEntity testBook1;
    private BookEntity testBook2;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        memberRepository.deleteAll();

        // Create test member using MemberService
        String uniqueEmail = "test-" + System.currentTimeMillis() + "@library.local";
        MemberInput memberInput = new MemberInput(
                "Test Member",
                uniqueEmail,
                "password123",
                "123 Main St",
                "555-1234"
        );
        var memberDto = memberService.createMember(memberInput);
        testMember = memberRepository.findById(memberDto.id()).orElseThrow();

        // Create test books
        testBook1 = BookEntity.builder()
                .title("Book One")
                .author("Author One")
                .isbn("ISBN-001")
                .totalCopies(5)
                .availableCopies(5)
                .build();
        testBook1 = bookRepository.save(testBook1);

        testBook2 = BookEntity.builder()
                .title("Book Two")
                .author("Author Two")
                .isbn("ISBN-002")
                .totalCopies(3)
                .availableCopies(3)
                .build();
        testBook2 = bookRepository.save(testBook2);
    }

    @Test
    void testLoanBooksSuccess() {
        List<Integer> bookIds = Arrays.asList(testBook1.getId(), testBook2.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);

        var result = loanService.loanBooks(loanInput);

        assertThat(result).isNotNull();
        assertThat(result.member().id()).isEqualTo(testMember.getId());
        assertThat(result.books()).hasSize(2);
        assertThat(result.books().get(0).borrowDate()).isNotNull();
        assertThat(result.books().get(0).dueDate()).isNotNull();
        assertThat(result.books().get(0).returnDate()).isNull();

        // Verify available copies decreased
        var book1 = bookRepository.findById(testBook1.getId()).get();
        assertThat(book1.getAvailableCopies()).isEqualTo(4);

        var book2 = bookRepository.findById(testBook2.getId()).get();
        assertThat(book2.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    void testLoanBooksNotAvailable() {
        // Set available copies to 0
        testBook1.setAvailableCopies(0);
        bookRepository.save(testBook1);

        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);

        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.loanBooks(loanInput));

        assertThat(exception.getMessage()).contains("not available");
    }

    @Test
    void testLoanBooksAlreadyHasActiveLoan() {
        // First loan
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        loanService.loanBooks(loanInput);

        // Try to loan same book again
        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.loanBooks(loanInput));

        assertThat(exception.getMessage()).contains("already has active loan");
    }

    @Test
    void testLoanBooksMemberNotFound() {
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(99999, bookIds);

        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.loanBooks(loanInput));

        assertThat(exception.getMessage()).contains("Member not found");
    }

    @Test
    void testLoanBooksNotFound() {
        List<Integer> bookIds = List.of(99999);
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);

        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.loanBooks(loanInput));

        assertThat(exception.getMessage()).contains("Book not found");
    }

    @Test
    void testReturnBooksSuccess() {
        // First loan books
        List<Integer> bookIds = Arrays.asList(testBook1.getId(), testBook2.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        // Get the loan IDs
        List<ReturnPairInput> returnPairs = loanResult.books().stream()
                .map(lb -> new ReturnPairInput(lb.id(), lb.book().id()))
                .toList();

        ReturnInput returnInput = new ReturnInput(testMember.getId(), returnPairs);
        var returnResult = loanService.returnBooks(returnInput);

        assertThat(returnResult).isNotNull();
        assertThat(returnResult.books()).hasSize(2);
        assertThat(returnResult.books().stream().allMatch(b -> b.returnDate() != null)).isTrue();

        // Verify available copies increased
        var book1 = bookRepository.findById(testBook1.getId()).get();
        assertThat(book1.getAvailableCopies()).isEqualTo(5);

        var book2 = bookRepository.findById(testBook2.getId()).get();
        assertThat(book2.getAvailableCopies()).isEqualTo(3);
    }

    @Test
    void testReturnBooksAlreadyReturned() {
        // Loan books
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        // Return books
        List<ReturnPairInput> returnPairs = loanResult.books().stream()
                .map(lb -> new ReturnPairInput(lb.id(), lb.book().id()))
                .toList();
        ReturnInput returnInput = new ReturnInput(testMember.getId(), returnPairs);
        loanService.returnBooks(returnInput);

        // Try to return same book again
        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.returnBooks(returnInput));

        assertThat(exception.getMessage()).contains("already returned");
    }

    @Test
    void testReturnBooksLoanNotFound() {
        List<ReturnPairInput> returnPairs = List.of(
                new ReturnPairInput(99999, testBook1.getId())
        );
        ReturnInput returnInput = new ReturnInput(testMember.getId(), returnPairs);

        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.returnBooks(returnInput));

        assertThat(exception.getMessage()).contains("Loan not found");
    }

    @Test
    void testReturnBooksWrongMember() {
        // Loan books as testMember
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        // Create another member using MemberService
        String uniqueEmail = "other-" + System.currentTimeMillis() + "@library.local";
        MemberInput otherMemberInput = new MemberInput(
                "Other Member",
                uniqueEmail,
                "password123",
                "456 Main St",
                "555-5678"
        );
        var otherMemberDto = memberService.createMember(otherMemberInput);
        MemberEntity otherMember = memberRepository.findById(otherMemberDto.id()).orElseThrow();

        // Try to return with wrong member
        List<ReturnPairInput> returnPairs = loanResult.books().stream()
                .map(lb -> new ReturnPairInput(lb.id(), lb.book().id()))
                .toList();
        ReturnInput returnInput = new ReturnInput(otherMember.getId(), returnPairs);

        var exception = assertThrows(IllegalArgumentException.class, 
            () -> loanService.returnBooks(returnInput));

        assertThat(exception.getMessage()).contains("does not belong to this member");
    }

    @Test
    void testGetLoansByMemberId() {
        // Loan books
        List<Integer> bookIds = Arrays.asList(testBook1.getId(), testBook2.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        loanService.loanBooks(loanInput);

        // Get loans for member
        LoanFilter filter = new LoanFilter(false, false, 0);
        var loans = loanService.getLoansByMemberId(testMember.getId(), filter);

        assertThat(loans).hasSize(2);
        assertThat(loans.stream().allMatch(l -> l.returnDate() == null)).isTrue();
    }

    @Test
    void testGetLoansByMemberIdOnlyActive() {
        // Loan books
        List<Integer> bookIds = Arrays.asList(testBook1.getId(), testBook2.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        // Return one book
        List<ReturnPairInput> returnPairs = Arrays.asList(
                new ReturnPairInput(loanResult.books().get(0).id(), testBook1.getId())
        );
        ReturnInput returnInput = new ReturnInput(testMember.getId(), returnPairs);
        loanService.returnBooks(returnInput);

        // Get only active loans
        LoanFilter filter = new LoanFilter(true, false, 0);
        var loans = loanService.getLoansByMemberId(testMember.getId(), filter);

        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).book().isbn()).isEqualTo("ISBN-002");
    }

    @Test
    void testGetAllLoans() {
        // Loan books
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        loanService.loanBooks(loanInput);

        LoanFilter filter = new LoanFilter(false, false, 0);
        var loans = loanService.getAllLoans(filter);

        assertThat(loans).isNotEmpty();
        assertThat(loans.get(0).member().id()).isEqualTo(testMember.getId());
        assertThat(loans.get(0).book().id()).isEqualTo(testBook1.getId());
    }

    @Test
    void testGetLoansByBook() {
        // Loan book
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        loanService.loanBooks(loanInput);

        LoanFilter filter = new LoanFilter(false, false, 0);
        var loans = loanService.getLoansByBook(testBook1.getId(), filter);

        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).book().id()).isEqualTo(testBook1.getId());
        assertThat(loans.get(0).member().id()).isEqualTo(testMember.getId());
    }

    @Test
    void testCacheLoanResult() {
        // Loan books
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        // Cache result
        String requestId = "test-loan-request-001";
        loanService.cacheLoanResult(requestId, loanResult);

        // Retrieve result
        var cachedResult = loanService.getLoanByRequestId(requestId);

        assertThat(cachedResult).isNotNull();
        assertThat(cachedResult.member().id()).isEqualTo(testMember.getId());
        assertThat(cachedResult.books()).hasSize(1);
    }

    @Test
    void testCacheReturnResult() {
        // Loan and return books
        List<Integer> bookIds = Collections.singletonList(testBook1.getId());
        LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
        var loanResult = loanService.loanBooks(loanInput);

        List<ReturnPairInput> returnPairs = loanResult.books().stream()
                .map(lb -> new ReturnPairInput(lb.id(), lb.book().id()))
                .toList();
        ReturnInput returnInput = new ReturnInput(testMember.getId(), returnPairs);
        var returnResult = loanService.returnBooks(returnInput);

        // Cache result
        String requestId = "test-return-request-001";
        loanService.cacheReturnResult(requestId, returnResult);

        // Retrieve result
        var cachedResult = loanService.getReturnByRequestId(requestId);

        assertThat(cachedResult).isNotNull();
        assertThat(cachedResult.member().id()).isEqualTo(testMember.getId());
        assertThat(cachedResult.books().stream().allMatch(b -> b.returnDate() != null)).isTrue();
    }

    @Test
    void testConcurrentLoansSameMember_OnlyOneSucceeds() throws InterruptedException {
        // Scenario 1: 2 concurrent calls with same member ID
        // Expected: Only 1 book deducted due to distributed locking

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Integer> bookIds = Collections.singletonList(testBook1.getId());

        // Submit 2 concurrent loan requests for the same member
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start simultaneously
                    LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
                    loanService.loanBooks(loanInput);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("already has active loan")) {
                        errorCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start both threads simultaneously
        startLatch.countDown();

        // Wait for both threads to complete
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify results: Only 1 request should succeed, 1 should fail with "already has active loan"
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(errorCount.get()).isEqualTo(1);

        // Verify only 1 book was deducted
        var book1 = bookRepository.findById(testBook1.getId()).get();
        assertThat(book1.getAvailableCopies()).isEqualTo(4); // 5 - 1 = 4
    }

    @Test
    void testConcurrentLoansDifferentMembers_BothSucceed() throws InterruptedException {
        // Scenario 2: 2 concurrent calls with different member IDs
        // Expected: Both succeed, 2 books deducted

        // Create second member
        String uniqueEmail2 = "test2-" + System.currentTimeMillis() + "@library.local";
        MemberInput memberInput2 = new MemberInput(
                "Test Member 2",
                uniqueEmail2,
                "password123",
                "456 Second St",
                "555-5678"
        );
        var memberDto2 = memberService.createMember(memberInput2);
        MemberEntity testMember2 = memberRepository.findById(memberDto2.id()).orElseThrow();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);

        List<Integer> bookIds = Collections.singletonList(testBook1.getId());

        // Submit 2 concurrent loan requests for different members
        executor.submit(() -> {
            try {
                startLatch.await();
                LoanInput loanInput = new LoanInput(testMember.getId(), bookIds);
                loanService.loanBooks(loanInput);
                successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                LoanInput loanInput = new LoanInput(testMember2.getId(), bookIds);
                loanService.loanBooks(loanInput);
                successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Start both threads simultaneously
        startLatch.countDown();

        // Wait for both threads to complete
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify results: Both requests should succeed
        assertThat(successCount.get()).isEqualTo(2);

        // Verify 2 books were deducted
        var book1 = bookRepository.findById(testBook1.getId()).get();
        assertThat(book1.getAvailableCopies()).isEqualTo(3); // 5 - 2 = 3
    }

    @Test
    void testConcurrentLoansLimitedAvailability_TwoSucceedOneFails() throws InterruptedException {
        // Scenario 3: 3 concurrent calls with only 2 books available
        // Expected: 2 succeed, 1 fails with "not available"

        // Create a book with only 2 copies
        BookEntity limitedBook = BookEntity.builder()
                .title("Limited Book")
                .author("Author")
                .isbn("ISBN-LTD-001")
                .totalCopies(2)
                .availableCopies(2)
                .build();
        limitedBook = bookRepository.save(limitedBook);

        // Create 3 members
        String uniqueEmail1 = "member1-" + System.currentTimeMillis() + "@library.local";
        String uniqueEmail2 = "member2-" + System.currentTimeMillis() + "@library.local";
        String uniqueEmail3 = "member3-" + System.currentTimeMillis() + "@library.local";

        var member1 = memberService.createMember(new MemberInput("Member 1", uniqueEmail1, "pass", "Addr1", "111"));
        var member2 = memberService.createMember(new MemberInput("Member 2", uniqueEmail2, "pass", "Addr2", "222"));
        var member3 = memberService.createMember(new MemberInput("Member 3", uniqueEmail3, "pass", "Addr3", "333"));

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger notAvailableErrorCount = new AtomicInteger(0);

        List<Integer> bookIds = Collections.singletonList(limitedBook.getId());

        // Submit 3 concurrent loan requests
        Integer finalLimitedBookId = limitedBook.getId();

        executor.submit(() -> {
            try {
                startLatch.await();
                LoanInput loanInput = new LoanInput(member1.id(), bookIds);
                loanService.loanBooks(loanInput);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("not available")) {
                    notAvailableErrorCount.incrementAndGet();
                } else {
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                LoanInput loanInput = new LoanInput(member2.id(), bookIds);
                loanService.loanBooks(loanInput);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("not available")) {
                    notAvailableErrorCount.incrementAndGet();
                } else {
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                LoanInput loanInput = new LoanInput(member3.id(), bookIds);
                loanService.loanBooks(loanInput);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("not available")) {
                    notAvailableErrorCount.incrementAndGet();
                } else {
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify results: 2 should succeed, 1 should fail with "not available"
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(notAvailableErrorCount.get()).isEqualTo(1);

        // Verify book availability is now 0
        var finalBook = bookRepository.findById(finalLimitedBookId).get();
        assertThat(finalBook.getAvailableCopies()).isEqualTo(0); // 2 - 2 = 0
    }


}

