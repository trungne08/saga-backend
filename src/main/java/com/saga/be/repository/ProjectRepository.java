package com.saga.be.repository;

import com.saga.be.entity.Project;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select project from Project project "
            + "left join fetch project.course course "
            + "left join fetch course.instructor "
            + "where project.id = :projectId")
    Optional<Project> findWithCourseAndInstructorById(
            @Param("projectId") UUID projectId
    );
}
