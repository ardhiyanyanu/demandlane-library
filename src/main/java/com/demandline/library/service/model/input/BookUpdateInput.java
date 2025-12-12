package com.demandline.library.service.model.input;

public record BookUpdateInput(
        Integer id,
        String title,
        String author,
        String isbn,
        Integer totalCopies
) {}
