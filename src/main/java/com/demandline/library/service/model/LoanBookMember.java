package com.demandline.library.service.model;

import java.time.LocalDateTime;

public record LoanBookMember(
        Integer id,
        Member member,
        Book book,
        LocalDateTime borrowDate,
        LocalDateTime returnDate,
        LocalDateTime dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
