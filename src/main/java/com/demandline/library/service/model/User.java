package com.demandline.library.service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Model
 * Represents library staff users in the system
 */
public record User(
    Integer id,
    String name,
    String email,
    String password,
    Role role,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Boolean isActive
){}

