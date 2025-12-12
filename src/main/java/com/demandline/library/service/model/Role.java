package com.demandline.library.service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Role Model
 * Represents user roles in the system (Admin, Librarian, Front Desk Staff, Member)
 */
public record Role(
    Integer id,
    String name,
    String permissions, // Stored as JSON array string
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
