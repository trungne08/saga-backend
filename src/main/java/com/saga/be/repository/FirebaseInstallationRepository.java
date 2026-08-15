package com.saga.be.repository;

import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.security.ApplicationRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FirebaseInstallationRepository extends JpaRepository<FirebaseInstallation, UUID> {

    Optional<FirebaseInstallation> findByFirebaseInstallationId(String firebaseInstallationId);

    Optional<FirebaseInstallation> findByIdAndOwnerProfileIdAndOwnerRole(
            UUID id,
            UUID ownerProfileId,
            ApplicationRole ownerRole
    );

    List<FirebaseInstallation> findByOwnerProfileIdAndOwnerRoleAndActiveTrue(
            UUID ownerProfileId,
            ApplicationRole ownerRole
    );
}
