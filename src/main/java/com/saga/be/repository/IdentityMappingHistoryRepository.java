package com.saga.be.repository;

import com.saga.be.entity.IdentityMappingHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdentityMappingHistoryRepository
        extends JpaRepository<IdentityMappingHistory, UUID> {
}
