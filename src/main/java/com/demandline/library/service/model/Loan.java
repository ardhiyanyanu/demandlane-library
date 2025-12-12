package com.demandline.library.service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Loan Model
 * Represents book borrowing/lending records in the library
 * Tracks which member borrowed which book and when
 */
public record Loan(
        Member member,
        List<LoanBook> books
) {}

