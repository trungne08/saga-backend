package com.saga.be.repository;

import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    boolean existsByTeamIdAndStudentIdAndRoleInTeam(
            UUID teamId,
            UUID studentId,
            RoleInTeam roleInTeam
    );

    Optional<TeamMember> findByTeamIdAndStudentId(UUID teamId, UUID studentId);

    boolean existsByTeamIdAndStudentId(UUID teamId, UUID studentId);

    boolean existsByStudentIdAndTeamCourseInstructorId(
            UUID studentId,
            UUID instructorId
    );

    List<TeamMember> findByTeamId(UUID teamId);

    List<TeamMember> findByStudentId(UUID studentId);

    @EntityGraph(attributePaths = "team")
    List<TeamMember> findByStudentIdAndTeamCourseId(UUID studentId, UUID courseId);

    @EntityGraph(attributePaths = {"student", "team", "team.course"})
    List<TeamMember> findAllByStudentIdAndTeamCourseId(
            UUID studentId,
            UUID courseId
    );

    @EntityGraph(attributePaths = {"student", "team", "team.project"})
    List<TeamMember> findByTeamCourseId(UUID courseId);

    @EntityGraph(attributePaths = {"student", "team", "team.project"})
    @Query("""
            select membership from TeamMember membership
            where membership.team.course.id = :courseId
            order by lower(membership.team.name), lower(membership.student.studentCode), membership.id
            """)
    List<TeamMember> findForCourseReportByCourseId(@Param("courseId") UUID courseId);

    @EntityGraph(attributePaths = {"student", "team", "team.project"})
    Page<TeamMember> findByTeamId(UUID teamId, Pageable pageable);
}
