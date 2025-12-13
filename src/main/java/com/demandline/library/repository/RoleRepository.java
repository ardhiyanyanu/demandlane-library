package com.demandline.library.repository;

import com.demandline.library.repository.model.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Role entity
 * Provides database operations for roles
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
    
    /**
     * Find a role by its name
     * @param name the role name
     * @return Optional containing the role if found
     */
    Optional<RoleEntity> findByName(String name);
}

