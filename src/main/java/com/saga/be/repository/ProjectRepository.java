package com.saga.be.repository;

import com.saga.be.entity.Project;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByCourseId(UUID courseId);

    @Override
    @EntityGraph(attributePaths = {"course"})
    Page<Project> findAll(Pageable pageable);

    @Query("select project from Project project "
            + "left join fetch project.course course "
            + "left join fetch course.instructor "
            + "left join fetch project.projectType "
            + "where project.id = :projectId")
    Optional<Project> findWithCourseAndInstructorById(
            @Param("projectId") UUID projectId
    );
}
