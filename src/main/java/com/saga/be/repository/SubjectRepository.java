package com.saga.be.repository;

import com.saga.be.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    boolean existsBySubjectCode(String subjectCode);

    boolean existsBySubjectCodeAndIdNot(String subjectCode, UUID id);

    Optional<Subject> findBySubjectCode(String subjectCode);

    Optional<Subject> findBySubjectCodeAndDeletedAtIsNull(String subjectCode);

    Optional<Subject> findByIdAndDeletedAtIsNull(UUID id);

    Page<Subject> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("""
            select subject
            from Subject subject
            where subject.deletedAt is null
              and (lower(subject.name) like lower(concat('%', :keyword, '%'))
                   or lower(subject.subjectCode) like lower(concat('%', :keyword, '%')))
            """)
    Page<Subject> searchActive(String keyword, Pageable pageable);
}
