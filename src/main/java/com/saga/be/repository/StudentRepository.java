package com.saga.be.repository;

import com.saga.be.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByCognitoSub(String cognitoSub);

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByStudentCode(String studentCode);

    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.id = :id")
    Optional<Student> findForIdentityBindingById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.id = :id")
    Optional<Student> findForTeamMembershipWriteById(@Param("id") UUID id);
}
