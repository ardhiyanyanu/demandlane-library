package com.demandline.library.controller;

import com.demandline.library.service.LoanService;
import com.demandline.library.service.MemberService;
import com.demandline.library.service.model.LoanBook;
import com.demandline.library.service.model.filter.LoanFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Member Self-Service Controller
 * Handles authenticated member self-service operations
 * Members can view their own borrowing history and current borrowed books
 */
@RestController
@RequestMapping("/library/member/me")
@Tag(name = "Member Self-Service", description = "Member self-service operations (Authenticated members only)")
@SecurityRequirement(name = "Bearer Authentication")
public class MemberSelfServiceController {
    private final MemberService memberService;
    private final LoanService loanService;

    public MemberSelfServiceController(MemberService memberService, LoanService loanService) {
        this.loanService = loanService;
        this.memberService = memberService;
    }

    @GetMapping("/loans")
    @Operation(
        summary = "View Own Borrowing History",
        description = "Retrieve complete borrowing history for authenticated member including active and returned books.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Borrowing history retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (MEMBER:READ required)")
    })
    public ResponseEntity<List<MemberLoanResponse>> getMyLoans(
        @Parameter(description = "Filter: active loans only") @RequestParam(required = false) Boolean activeOnly) {
        // TODO : change 0 to authenticated member ID
        var results = loanService.getLoansByMemberId(0, new LoanFilter(
                activeOnly,
                false,
                0
        ));
        return ResponseEntity.ok(results.stream().map(MemberLoanResponse::new).toList());
    }

    // Response DTOs
    public record MemberLoanResponse(
        Integer loanId,
        String bookTitle,
        String bookAuthor,
        String isbn,
        LocalDateTime borrowDate,
        LocalDateTime returnDate,
        LocalDateTime dueDate,
        Boolean isOverdue
    ) {
        public MemberLoanResponse(LoanBook loanBook) {
            this(
                loanBook.id(),
                loanBook.book().title(),
                loanBook.book().author(),
                loanBook.book().isbn(),
                loanBook.borrowDate(),
                loanBook.returnDate(),
                loanBook.dueDate(),
                loanBook.returnDate() != null && loanBook.returnDate().isAfter(loanBook.dueDate())
            );
        }
    }
}

