package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtraMasterEarlyWarningV2Test {

    @Test
    void policyConstantsMatchAcceptedMilestoneRules() {
        assertEquals("TBD_PRODUCT", EarlyWarningPolicy.INACTIVITY_GRACE_PERIOD);
        assertEquals(72, EarlyWarningPolicy.ACTIVITY_WINDOW.toHours());
        assertEquals(0.40d, EarlyWarningPolicy.SPRINT_START_EVALUATION);
        assertEquals(0.25d, EarlyWarningPolicy.SPRINT_WARNING_GAP);
        assertEquals(0.40d, EarlyWarningPolicy.SPRINT_CRITICAL_GAP);
        assertEquals(3, EarlyWarningPolicy.REPEATED_WINDOW);
        assertEquals(2, EarlyWarningPolicy.REPEATED_THRESHOLD);
        assertEquals(20, EarlyWarningPolicy.HISTORICAL_DISCOVERY_PAGE);
    }

    @Test
    void unknownMembershipAgeAndRecentActivityDoNotEmitInactivity() {
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        IdentityMapRepository maps = mock(IdentityMapRepository.class);
        CommitDataRepository commits = mock(CommitDataRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        BusinessWarningService warnings = mock(BusinessWarningService.class);
        EarlyWarningV2Service service = new EarlyWarningV2Service(
                teams, members, maps, commits, documents, sprints, tasks,
                mock(CommitReviewResultRepository.class), warnings
        );

        Project project = Project.builder().name("P").build();
        project.setId(UUID.randomUUID());
        Team team = Team.builder().name("T").project(project).build();
        team.setId(UUID.randomUUID());
        Student student = Student.builder().fullName("A").studentCode("SE1").build();
        student.setId(UUID.randomUUID());
        TeamMember unknownAge = TeamMember.builder()
                .team(team).student(student).roleInTeam(RoleInTeam.MEMBER).build();
        unknownAge.setId(UUID.randomUUID());

        Student recent = Student.builder().fullName("B").studentCode("SE2").build();
        recent.setId(UUID.randomUUID());
        TeamMember recentMember = TeamMember.builder()
                .team(team).student(recent).roleInTeam(RoleInTeam.LEADER).build();
        recentMember.setId(UUID.randomUUID());
        recentMember.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(10));

        IdentityMap mapping = IdentityMap.builder()
                .provider(IntegrationProvider.GITHUB)
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .externalAccountId("99")
                .student(recent)
                .build();
        mapping.setId(UUID.randomUUID());

        when(teams.findAll()).thenReturn(List.of(team));
        when(members.findByTeamId(team.getId())).thenReturn(List.of(unknownAge, recentMember));
        when(maps.findByStudentIdOrderByProvider(recent.getId())).thenReturn(List.of(mapping));
        when(commits.findLatestMappedTimestamp(project.getId(), recent.getId(), "99"))
                .thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(documents.findLatestCreatedAtByProjectAndAuthor(project.getId(), recent.getId()))
                .thenReturn(null);
        when(sprints.findByBoardProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of());

        service.scanBounded();

        verify(warnings, never()).emit(any());
    }

    @Test
    void sprintElapsedBelowStartThresholdDoesNotWarnAndTaskCountModeIsUsedWhenSpIncomplete() {
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        BusinessWarningService warnings = mock(BusinessWarningService.class);
        EarlyWarningV2Service service = new EarlyWarningV2Service(
                teams, members, mock(IdentityMapRepository.class), mock(CommitDataRepository.class),
                mock(DocumentRepository.class), sprints, tasks,
                mock(CommitReviewResultRepository.class), warnings
        );
        Project project = Project.builder().name("P").build();
        project.setId(UUID.randomUUID());
        Team team = Team.builder().name("T").project(project).build();
        team.setId(UUID.randomUUID());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Sprint sprint = Sprint.builder()
                .name("S1")
                .state("active")
                .startDate(now.minusDays(2))
                .endDate(now.plusDays(8))
                .build();
        sprint.setId(UUID.randomUUID());
        Task withSp = Task.builder().title("A").status(TaskStatus.TODO).storyPoint(5).build();
        withSp.setId(UUID.randomUUID());
        Task withoutSp = Task.builder().title("B").status(TaskStatus.DONE).build();
        withoutSp.setId(UUID.randomUUID());
        when(teams.findAll()).thenReturn(List.of(team));
        when(members.findByTeamId(team.getId())).thenReturn(List.of());
        when(sprints.findByBoardProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of(sprint));
        when(tasks.findBySprintIdAndDeletedAtIsNull(sprint.getId())).thenReturn(List.of(withSp, withoutSp));

        service.scanBounded();

        verify(warnings, never()).emit(any());
    }
}
