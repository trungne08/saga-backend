package com.saga.be.service;

import com.saga.be.entity.BusinessWarning;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Notification;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.BusinessWarningCategory;
import com.saga.be.entity.enums.BusinessWarningSeverity;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.SprintProgressMode;
import com.saga.be.repository.BusinessWarningRepository;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessWarningService {

    private final BusinessWarningRepository warnings;
    private final NotificationService notifications;
    private final WarningEmailOutboxService warningEmails;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final IdentityMapRepository identityMaps;
    private final StudentRepository students;

    public BusinessWarningService(
            BusinessWarningRepository warnings,
            NotificationService notifications,
            WarningEmailOutboxService warningEmails,
            TeamRepository teams,
            TeamMemberRepository teamMembers,
            IdentityMapRepository identityMaps,
            StudentRepository students
    ) {
        this.warnings = warnings;
        this.notifications = notifications;
        this.warningEmails = warningEmails;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.identityMaps = identityMaps;
        this.students = students;
    }

    @Transactional
    public BusinessWarning emit(WarningDraft draft) {
        if (draft == null || draft.eventKey() == null || draft.eventKey().isBlank()) {
            throw new IllegalArgumentException("eventKey is invalid");
        }
        Optional<BusinessWarning> existing = warnings.findByEventKey(draft.eventKey());
        BusinessWarning warning = existing.orElseGet(() -> warnings.saveAndFlush(BusinessWarning.builder()
                .warningType(draft.type())
                .category(draft.category())
                .eventKey(draft.eventKey())
                .severity(draft.severity())
                .teamId(draft.teamId())
                .projectId(draft.projectId())
                .sprintId(draft.sprintId())
                .studentId(draft.studentId())
                .commitSha(draft.commitSha())
                .evidenceSummary(draft.evidenceSummary())
                .progressMode(draft.progressMode())
                .build()));
        for (Recipient recipient : draft.recipients()) {
            if (recipient == null || recipient.profileId() == null || recipient.role() == null) {
                continue;
            }
            Notification bell = notifications.createOnceForEvent(
                    recipient.profileId(),
                    recipient.role(),
                    draft.type(),
                    draft.title(),
                    draft.evidenceSummary(),
                    draft.eventKey()
            );
            warningEmails.enqueueOnce(bell);
        }
        return warning;
    }

    public List<Recipient> leadersOfOwningTeam(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        Team team = teams.findByProjectId(projectId).orElse(null);
        if (team == null) {
            return List.of();
        }
        return leadersOfTeam(team.getId());
    }

    public List<Recipient> leadersOfTeam(UUID teamId) {
        if (teamId == null) {
            return List.of();
        }
        List<Recipient> leaders = new ArrayList<>();
        for (TeamMember member : teamMembers.findByTeamIdAndRoleInTeam(teamId, RoleInTeam.LEADER)) {
            if (member.getStudent() == null || member.getStudent().getId() == null) {
                continue;
            }
            leaders.add(new Recipient(member.getStudent().getId(), ApplicationRole.STUDENT));
        }
        return List.copyOf(leaders);
    }

    public Optional<Recipient> uniqueActiveGithubAuthor(String githubNumericId) {
        if (githubNumericId == null || githubNumericId.isBlank()) {
            return Optional.empty();
        }
        Optional<IdentityMap> mapping = identityMaps.findByProviderAndExternalAccountIdAndMappingStatus(
                IntegrationProvider.GITHUB,
                githubNumericId.trim(),
                IdentityMappingStatus.ACTIVE
        );
        if (mapping.isEmpty() || mapping.get().getStudent() == null) {
            return Optional.empty();
        }
        Student student = mapping.get().getStudent();
        return Optional.of(new Recipient(student.getId(), ApplicationRole.STUDENT));
    }

    public Optional<Student> student(UUID studentId) {
        return studentId == null ? Optional.empty() : students.findById(studentId);
    }

    public record Recipient(UUID profileId, ApplicationRole role) {
    }

    public record WarningDraft(
            NotificationType type,
            BusinessWarningCategory category,
            String eventKey,
            String title,
            String evidenceSummary,
            BusinessWarningSeverity severity,
            UUID teamId,
            UUID projectId,
            UUID sprintId,
            UUID studentId,
            String commitSha,
            SprintProgressMode progressMode,
            List<Recipient> recipients
    ) {
    }
}
