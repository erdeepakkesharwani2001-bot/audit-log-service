package com.schwab.audit.repository;

import com.schwab.audit.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for RetentionPolicy entities.
 */
@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    
    Optional<RetentionPolicy> findByPolicyName(String policyName);
    
    boolean existsByPolicyName(String policyName);
}
