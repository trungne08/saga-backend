package com.saga.be.repository;

import com.saga.be.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    boolean existsBySubjectCode(String subjectCode);

    Optional<Subject> findBySubjectCode(String subjectCode);

    Page<Subject> findByNameContainingIgnoreCaseOrSubjectCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}
