package com.demandline.library.repository;

import com.demandline.library.repository.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository interface for User entity
 * Provides database operations for users (library staff)
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    
    /**
     * Find a user by email
     * @param email the user email
     * @return Optional containing the user if found
     */
    Optional<UserEntity> findByEmail(String email);
    
    /**
     * Find all active users
     * @return List of active users
     */
    List<UserEntity> findAllByActiveTrue();

    /**
     * Find users by role name
     * @param roleName the role name
     * @return List of users with the specified role
     */
    @Query("SELECT u FROM UserEntity u WHERE u.roleEntity.name = :roleName AND u.active = TRUE")
    List<UserEntity> findByRoleNameAndActiveTrue(@Param("roleName") String roleName);
}
