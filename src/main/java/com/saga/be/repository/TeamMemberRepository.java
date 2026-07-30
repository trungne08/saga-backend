package com.saga.be.repository;

import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    boolean existsByTeamIdAndStudentIdAndRoleInTeam(
            UUID teamId,
            UUID studentId,
            RoleInTeam roleInTeam
    );

    Optional<TeamMember> findByTeamIdAndStudentId(UUID teamId, UUID studentId);

    boolean existsByStudentIdAndTeamCourseInstructorId(
            UUID studentId,
            UUID instructorId
    );
}
