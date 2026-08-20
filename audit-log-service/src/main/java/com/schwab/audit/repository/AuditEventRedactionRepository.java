package com.schwab.audit.repository;

import com.schwab.audit.entity.AuditEventRedaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AuditEventRedaction entities.
 */
@Repository
public interface AuditEventRedactionRepository extends JpaRepository<AuditEventRedaction, Long> {
    
    Optional<AuditEventRedaction> findByAuditEventId(Long auditEventId);
    
    List<AuditEventRedaction> findAllByAuditEventId(Long auditEventId);
    
    boolean existsByAuditEventId(Long auditEventId);
}
