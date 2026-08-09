package com.saga.be.repository;

import com.saga.be.entity.Admin;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByCognitoSub(String cognitoSub);

    Optional<Admin> findByEmailIgnoreCase(String email);

    @Query("select admin from Admin admin where lower(admin.email) in :emails")
    List<Admin> findAllByNormalizedEmailIn(@Param("emails") Collection<String> emails);
}
