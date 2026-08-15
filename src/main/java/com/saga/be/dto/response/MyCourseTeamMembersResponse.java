package com.saga.be.dto.response;

import com.saga.be.entity.Project;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record MyCourseTeamMembersResponse(
        UUID courseId,
        UUID teamId,
        String teamName,
        RoleInTeam roleInTeam,
        ProjectSummary project,
        Page<TeamMemberResponse> members
) {

    public static MyCourseTeamMembersResponse from(
            UUID courseId,
            TeamMember currentMembership,
            Page<TeamMemberResponse> members
    ) {
        Project project = currentMembership.getTeam().getProject();
        return new MyCourseTeamMembersResponse(
                courseId,
                currentMembership.getTeam().getId(),
                currentMembership.getTeam().getName(),
                currentMembership.getRoleInTeam(),
                project == null ? null : new ProjectSummary(project.getId(), project.getName()),
                members
        );
    }

    public record ProjectSummary(UUID id, String name) {
    }
}
