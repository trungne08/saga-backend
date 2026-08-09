package com.saga.be.repository;

import com.saga.be.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, UUID> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    Optional<Semester> findByCode(String code);

    Optional<Semester> findByIdAndDeletedAtIsNull(UUID id);

    Page<Semester> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("""
            select semester
            from Semester semester
            where semester.deletedAt is null
              and (lower(semester.name) like lower(concat('%', :keyword, '%'))
                   or lower(semester.code) like lower(concat('%', :keyword, '%')))
            """)
    Page<Semester> searchActive(String keyword, Pageable pageable);
}
