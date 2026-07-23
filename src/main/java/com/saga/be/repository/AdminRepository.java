package com.saga.be.repository;

import com.saga.be.entity.Admin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByCognitoSub(String cognitoSub);

    Optional<Admin> findByEmailIgnoreCase(String email);
}
