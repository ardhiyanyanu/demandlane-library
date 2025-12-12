package com.demandline.library.repository;

import com.demandline.library.repository.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository interface for User entity
 * Provides database operations for users (library staff)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    
    /**
     * Find a user by email
     * @param email the user email
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find all active users
     * @return List of active users
     */
    List<User> findAllByIsActiveTrue();
    
    /**
     * Find users by role name
     * @param roleName the role name
     * @return List of users with the specified role
     */
    List<User> findByRoleNameAndIsActiveTrue(String roleName);
}

