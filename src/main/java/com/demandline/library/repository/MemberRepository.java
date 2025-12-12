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
    

}

