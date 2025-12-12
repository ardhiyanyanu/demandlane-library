package com.demandline.library.controller;

import com.demandline.library.service.LoanService;
import com.demandline.library.service.model.Loan;
import com.demandline.library.service.model.LoanBook;
import com.demandline.library.service.model.LoanBookMember;
import com.demandline.library.service.model.filter.LoanFilter;
import com.demandline.library.service.model.input.LoanInput;
import com.demandline.library.service.model.input.ReturnInput;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Loan Management Controller
 * Handles book borrowing and returning operations
 * Requires BORROW and MEMBER related permissions based on operation
 */
@RestController
@RequestMapping("/library/admin/loans")
@Tag(name = "Loan Management", description = "Book borrowing and returning operations (Front Desk Staff access)")
@SecurityRequirement(name = "Bearer Authentication")
public class LoanController {
    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/borrow")
    @Operation(
        summary = "Borrow Book",
        description = "Process book borrowing for a member. System checks book availability and member eligibility. " +
                      "Implements pessimistic locking to prevent double borrowing of same book.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Book borrowed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or business logic violation"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BORROW:CREATE required)"),
        @ApiResponse(responseCode = "404", description = "Member or book not found"),
        @ApiResponse(responseCode = "409", description = "Book not available or member has active loan for this book")
    })
    public ResponseEntity<LoanCreateResponse> borrowBook(@RequestBody BorrowRequest request) {
        var loanResult = loanService.getLoanByRequestId(request.requestId());
        if (loanResult != null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new LoanCreateResponse(loanResult));
        }
        loanResult = loanService.loanBooks(new LoanInput(
                request.memberId,
                request.bookId
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(new LoanCreateResponse(loanResult));
    }

    @PostMapping("/return")
    @Operation(
        summary = "Return Book",
        description = "Process book return for a member. System updates inventory and member's borrowing history. " +
                      "Checks for overdue books and applies necessary penalties.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Book returned successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or business logic violation"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BORROW:UPDATE required)"),
        @ApiResponse(responseCode = "404", description = "Loan record not found")
    })
    public ResponseEntity<LoanReturnResponse> returnBook(@RequestBody ReturnRequest request) {
        var loanResult = loanService.getReturnByRequestId(request.requestId());
        if (loanResult != null) {
            return ResponseEntity.ok(new LoanReturnResponse(loanResult));
        }
        loanResult = loanService.returnBooks(new ReturnInput(
                request.memberId,
                request.returnRequests().stream()
                        .map(r -> new com.demandline.library.service.model.input.ReturnPairInput(
                                r.loanId(),
                                r.bookId()
                        )).toList()
        ));
        return ResponseEntity.ok(new LoanReturnResponse(loanResult));
    }

    @GetMapping("/member/{memberId}")
    @Operation(
        summary = "View Member's Borrowing History",
        description = "Retrieve complete borrowing history for a specific member including active and returned books.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Borrowing history retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BORROW:READ required)"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<List<LoanHistoryResponse>> getMemberLoans(
        @Parameter(description = "Member ID") @PathVariable Integer memberId,
        @Parameter(description = "Filter: active loans only") @RequestParam(required = false) Boolean activeOnly,
        @Parameter(description = "Days overdue") @RequestParam(defaultValue = "0") Integer daysOverdue) {
        var results = loanService.getLoansByMemberId(memberId, new LoanFilter(
                activeOnly,
                false,
                daysOverdue
        ));
        return ResponseEntity.ok(results.stream().map(LoanHistoryResponse::new).toList());
    }

    @GetMapping("/overdue")
    @Operation(
        summary = "List Overdue Loans",
        description = "Retrieve list of all overdue loans (where return_date is null and due_date has passed).",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Overdue loans list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BORROW:READ required)")
    })
    public ResponseEntity<List<OverdueLoanResponse>> getOverdueLoans(
        @Parameter(description = "Page number for pagination") @RequestParam(defaultValue = "0") Integer page,
        @Parameter(description = "Page size for pagination") @RequestParam(defaultValue = "20") Integer size) {
        var results = loanService.getAllLoans(new LoanFilter(
                true,
                true,
                0
        ));
        return ResponseEntity.ok(results.stream().map(OverdueLoanResponse::new).toList());
    }

    @GetMapping("/book/{bookId}")
    @Operation(
        summary = "View All Loans for a Book",
        description = "Retrieve all loans (active and returned) for a specific book to track its borrowing history.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Book loans list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (BORROW:READ required)"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<List<LoanHistoryResponse>> getBookLoans(
        @Parameter(description = "Book ID") @PathVariable Integer bookId,
        @Parameter(description = "Filter: active loans only") @RequestParam(required = false) Boolean activeOnly) {
        var result = loanService.getLoansByBook(bookId, new LoanFilter(
                activeOnly,
                false,
                0
        ));
        return ResponseEntity.ok(result.stream().map(LoanHistoryResponse::new).toList());
    }

    // Request/Response DTOs
    public record BorrowRequest(
        Integer memberId,
        List<Integer> bookId,
        String requestId
    ) {}

    public record ReturnBookLoanRequest(
            Integer loanId,
            Integer bookId
    ) {}

    public record ReturnRequest(
        Integer memberId,
        List<ReturnBookLoanRequest> returnRequests,
        String requestId
    ) {}

    public record LoanBookResponse(
            Integer loanId,
            Integer bookId,
            LocalDateTime borrowDate,
            LocalDateTime dueDate,
            String message
    ) {
        public LoanBookResponse(LoanBook loan) {
            this(
                loan.id(),
                loan.book().id(),
                loan.borrowDate(),
                loan.dueDate(),
                "Book borrowed successfully"
            );
        }
    }

    public record LoanCreateResponse(
        Integer memberId,
        List<LoanBookResponse> bookLoan
    ) {
        public LoanCreateResponse(Loan loan) {
            this(loan.member().id(), loan.books().stream().map(LoanBookResponse::new).toList());
        }
    }

    public record LoanReturnBookResponse(
            Integer loanId,
            Integer bookId,
            LocalDateTime borrowDate,
            LocalDateTime dueDate,
            LocalDateTime returnDate,
            String message
    ) {
        public LoanReturnBookResponse(LoanBook loan) {
            this(
                    loan.id(),
                    loan.book().id(),
                    loan.borrowDate(),
                    loan.dueDate(),
                    loan.returnDate(),
                    "Book returned successfully"
            );
        }
    }

    public record LoanReturnResponse(
        Integer memberId,
        List<LoanReturnBookResponse> bookReturn
    ) {
        public LoanReturnResponse(Loan loan) {
            this(loan.member().id(), loan.books().stream().map(LoanReturnBookResponse::new).toList());
        }
    }

    public record LoanHistoryResponse(
        Integer loanId,
        String bookTitle,
        String bookAuthor,
        String isbn,
        LocalDateTime borrowDate,
        LocalDateTime returnDate,
        LocalDateTime dueDate,
        Boolean isOverdue
    ) {
        public LoanHistoryResponse(LoanBook loan) {
            this(
                loan.id(),
                loan.book().title(),
                loan.book().author(),
                loan.book().isbn(),
                loan.borrowDate(),
                loan.returnDate(),
                loan.dueDate(),
                loan.returnDate() == null && loan.dueDate().isBefore(LocalDateTime.now())
            );
        }

        public LoanHistoryResponse(LoanBookMember loan) {
            this(
                    loan.id(),
                    loan.book().title(),
                    loan.book().author(),
                    loan.book().isbn(),
                    loan.borrowDate(),
                    loan.returnDate(),
                    loan.dueDate(),
                    loan.returnDate() == null && loan.dueDate().isBefore(LocalDateTime.now())
            );
        }
    }

    public record OverdueLoanResponse(
        Integer loanId,
        Integer memberId,
        String memberName,
        String bookTitle,
        String isbn,
        LocalDateTime dueDate,
        Long daysOverdue
    ) {
        public OverdueLoanResponse(LoanBookMember loan) {
            this(
                loan.id(),
                loan.member().id(),
                loan.member().user().name(),
                loan.book().title(),
                loan.book().isbn(),
                loan.dueDate(),
                java.time.Duration.between(loan.dueDate(), LocalDateTime.now()).toDays()
            );
        }
    }
}

