package com.demandline.library.service.model.input;

public record BookInput(
        String title,
        String author,
        String isbn,
        Integer totalCopies
) {}
