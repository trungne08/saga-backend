package com.saga.be.repository;

import com.saga.be.entity.Assessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findByStudentId(UUID studentId);
}
