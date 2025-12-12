package com.demandline.library.repository;

import com.demandline.library.repository.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository interface for Member entity
 * Provides database operations for library members
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Integer> {
    
    /**
     * Find a member by user id
     * @param userId the user id
     * @return Optional containing the member if found
     */
    Optional<Member> findByUserId(Integer userId);
    
    /**
     * Find all active members
     * @return List of active members
     */
    List<Member> findAllByIsActiveTrue();
    
    /**
     * Check if a member exists and is active
     * @param memberId the member id
     * @return true if member exists and is active
     */
    boolean existsByIdAndIsActiveTrue(Integer memberId);
}

