package com.saga.be.repository;

import com.saga.be.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByCognitoSub(String cognitoSub);

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByStudentCode(String studentCode);

    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    @Query("select student from Student student where lower(student.email) in :emails")
    List<Student> findAllByNormalizedEmailIn(@Param("emails") Collection<String> emails);

    @Query("select student from Student student where lower(student.studentCode) in :studentCodes")
    List<Student> findAllByNormalizedStudentCodeIn(@Param("studentCodes") Collection<String> studentCodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.id = :id")
    Optional<Student> findForIdentityBindingById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.id = :id")
    Optional<Student> findForTeamMembershipWriteById(@Param("id") UUID id);

    @Query("select student.id from Student student order by student.id")
    Page<UUID> findAllIds(Pageable pageable);
}
