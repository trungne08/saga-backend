package com.saga.be.repository;

import com.saga.be.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, UUID> {
    boolean existsByCode(String code);

    Optional<Semester> findByCode(String code);

    Page<Semester> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}
