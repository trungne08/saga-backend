package com.saga.be.repository;

import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdentityMapRepository extends JpaRepository<IdentityMap, UUID> {

    List<IdentityMap> findByStudentIdOrderByProvider(UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdentityMap> findByStudentIdAndProvider(
            UUID studentId,
            IntegrationProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdentityMap> findByProviderAndExternalAccountId(
            IntegrationProvider provider,
            String externalAccountId
    );

    Optional<IdentityMap> findByProviderAndExternalAccountIdAndMappingStatus(
            IntegrationProvider provider,
            String externalAccountId,
            IdentityMappingStatus mappingStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mapping from IdentityMap mapping where mapping.id = :id")
    Optional<IdentityMap> findLockedById(@Param("id") UUID id);
}
