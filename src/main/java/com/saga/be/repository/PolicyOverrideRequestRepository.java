package com.saga.be.repository;

import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyOverrideRequestRepository extends JpaRepository<PolicyOverrideRequest, UUID> {
    List<PolicyOverrideRequest> findByTypeAndStatusAndClazz_Id(String type, PolicyOverrideStatus status, UUID classId);
}
