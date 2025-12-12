package com.demandline.library.service;

import com.demandline.library.config.LibraryConfiguration;
import com.demandline.library.repository.LoanRepository;
import com.demandline.library.service.model.Loan;
import com.demandline.library.service.model.LoanBook;
import com.demandline.library.service.model.LoanBookMember;
import com.demandline.library.service.model.filter.LoanFilter;
import com.demandline.library.service.model.input.LoanInput;
import com.demandline.library.service.model.input.ReturnInput;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoanService {
    private final BookService bookService;
    private final MemberService memberService;
    private final LibraryConfiguration libraryConfiguration;
    private final LoanRepository loanRepository;

    public LoanService(BookService bookService,
                       MemberService memberService,
                       LibraryConfiguration libraryConfiguration,
                       LoanRepository loanRepository) {
        this.bookService = bookService;
        this.memberService = memberService;
        this.libraryConfiguration = libraryConfiguration;
        this.loanRepository = loanRepository;
    }

    public Loan loanBooks(LoanInput loanInput) {
        return null;
    }

    public Loan getLoanByRequestId(String requestId) {
        return null;
    }

    public Loan returnBooks(ReturnInput returnInput) {
        return null;
    }

    public Loan getReturnByRequestId(String requestId) {
        return null;
    }

    public List<LoanBook> getLoansByMemberId(Integer memberId, LoanFilter filter) {
        return List.of();
    }

    public List<LoanBookMember> getAllLoans(LoanFilter filter) {
        return List.of();
    }

    public List<LoanBookMember> getLoansByBook(Integer bookId, LoanFilter filter) {
        return List.of();
    }
}
