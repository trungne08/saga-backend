package com.saga.be.repository;

import com.saga.be.entity.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByCognitoSub(String cognitoSub);

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByStudentCode(String studentCode);
}
