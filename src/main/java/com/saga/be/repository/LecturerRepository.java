package com.saga.be.repository;

import com.saga.be.entity.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, UUID> {
    Optional<Lecturer> findByCognitoSub(String cognitoSub);

    Optional<Lecturer> findByEmailIgnoreCase(String email);
}
