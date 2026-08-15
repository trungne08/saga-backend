package com.saga.be.repository;

import com.saga.be.entity.Class;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<Class, UUID> {
    boolean existsByClassCode(String classCode);

    boolean existsByClassCodeAndIdNot(String classCode, UUID id);

    Optional<Class> findByClassCode(String classCode);

    Optional<Class> findByClassCodeAndDeletedAtIsNull(String classCode);

    Optional<Class> findByIdAndDeletedAtIsNull(UUID id);

    Page<Class> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("""
            select clazz
            from Class clazz
            where clazz.deletedAt is null
              and (lower(clazz.name) like lower(concat('%', :keyword, '%'))
                   or lower(clazz.classCode) like lower(concat('%', :keyword, '%')))
            """)
    Page<Class> searchActive(String keyword, Pageable pageable);
}
