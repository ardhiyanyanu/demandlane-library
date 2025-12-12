package com.demandline.library.service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Book Model
 * Represents books in the library inventory
 */
public record Book(
    Integer id,
    String title,
    String author,
    String isbn,
    Integer totalCopies,
    Integer availableCopies,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

