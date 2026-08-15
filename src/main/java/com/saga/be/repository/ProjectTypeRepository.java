package com.saga.be.repository;

import com.saga.be.entity.ProjectType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTypeRepository extends JpaRepository<ProjectType, UUID> {

    List<ProjectType> findAllByOrderByNameAsc();

    Optional<ProjectType> findByCode(String code);
}
