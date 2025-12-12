package com.demandline.library.service.model.filter;

public record LoanFilter(
    boolean onlyActiveLoans,
    boolean onlyOverdueLoans,
    int daysOverdue
) {}
