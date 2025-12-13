package com.demandline.library.repository;

import com.demandline.library.repository.model.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Member entity
 * Provides database operations for library members
 */
@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Integer> {
    
    /**
     * Find a member by user email
     * @param email the user email
     * @return Optional containing the member if found
     */
    @Query("SELECT m FROM MemberEntity m WHERE m.userEntity.email = :email")
    Optional<MemberEntity> findByUserEmail(@Param("email") String email);

    /**
     * Find all active members
     * @return List of active members
     */
    List<MemberEntity> findAllByIsActiveTrue();

    /**
     * Find a member by user id
     * @param userId the user id
     * @return Optional containing the member if found
     */
    Optional<MemberEntity> findByUserEntityId(Integer userId);

    /**
     * Search members by name or email
     * @param searchTerm the search term
     * @return List of members matching name or email
     */
    @Query("SELECT m FROM MemberEntity m WHERE " +
           "LOWER(m.userEntity.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(m.userEntity.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<MemberEntity> searchMembers(@Param("searchTerm") String searchTerm);
}



