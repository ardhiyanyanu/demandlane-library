package com.demandline.library.service.model.filter;

import java.util.Optional;

public record BookFilter(
        Optional<String> bookName,
        Optional<String> authorName,
        Optional<String> isbn,
        boolean showNotAvailable
) {}
