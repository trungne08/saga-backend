package com.saga.be.repository;

import com.saga.be.entity.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, UUID>, JpaSpecificationExecutor<Lecturer> {
    Optional<Lecturer> findByCognitoSub(String cognitoSub);

    Optional<Lecturer> findByEmailIgnoreCase(String email);

    @Query("select lecturer from Lecturer lecturer where lower(lecturer.email) in :emails")
    List<Lecturer> findAllByNormalizedEmailIn(@Param("emails") Collection<String> emails);

    @Query("select lecturer.id from Lecturer lecturer order by lecturer.id")
    Page<UUID> findAllIds(Pageable pageable);
}
