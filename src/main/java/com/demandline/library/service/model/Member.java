package com.demandline.library.service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Member Model
 * Represents library members/patrons in the system
 */
public record Member(
    Integer id,
    User user,
    String address,
    String phoneNumber,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Boolean isActive
) {}
