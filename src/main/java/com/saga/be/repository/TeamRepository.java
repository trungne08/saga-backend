package com.saga.be.repository;

import com.saga.be.entity.Team;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    @Override
    @EntityGraph(attributePaths = {"course", "project"})
    Page<Team> findAll(Pageable pageable);

    Optional<Team> findByProjectId(UUID projectId);

    Optional<Team> findByCourseIdAndName(UUID courseId, String name);

    List<Team> findByCourseIdAndNameIn(UUID courseId, java.util.Collection<String> names);

    List<Team> findByCourseId(UUID courseId);

    @EntityGraph(attributePaths = {"course", "project"})
    List<Team> findByCourseIdOrderByNameAscIdAsc(UUID courseId);

    boolean existsByCourseId(UUID courseId);

    @Query("select team from Team team "
            + "left join fetch team.course course "
            + "left join fetch course.instructor "
            + "where team.id = :teamId")
    Optional<Team> findWithCourseAndInstructorById(@Param("teamId") UUID teamId);

    @Query("select team from Team team "
            + "left join fetch team.course course "
            + "left join fetch course.instructor "
            + "where team.project.id = :projectId")
    Optional<Team> findWithCourseAndInstructorByProjectId(
            @Param("projectId") UUID projectId
    );
}
