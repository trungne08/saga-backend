package com.saga.be.repository;

import com.saga.be.entity.Class;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface ClassRepository extends JpaRepository<Class, UUID> {
    boolean existsByClassCode(String classCode);

    Page<Class> findByNameContainingIgnoreCaseOrClassCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}