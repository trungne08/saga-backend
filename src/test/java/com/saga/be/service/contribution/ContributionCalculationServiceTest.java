package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributionCalculationServiceTest {

    @Test
    void calculatesProjectScopedScoresPercentsAndMixedOverrideDeterministically() {
        Fixture fixture = fixture();
        Task firstCodeTask = new Task();
        firstCodeTask.setStoryPoint(1);
        firstCodeTask.setLabels(List.of("backend"));
        Task secondCodeTask = new Task();
        secondCodeTask.setStoryPoint(1);
        secondCodeTask.setLabels(List.of("backend"));
        Task secondDesignTask = new Task();
        secondDesignTask.setStoryPoint(1);
        secondDesignTask.setLabels(List.of("ui-ux", "design"));
        when(fixture.commitRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(
                fixture.first.getId(), fixture.projectId)).thenReturn(List.of(
                        commitWithTask(firstCodeTask),
                        commitWithTask(secondCodeTask)
                ));
        when(fixture.commitRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(
                fixture.second.getId(), fixture.projectId)).thenReturn(List.of(
                        commitWithTask(secondDesignTask)
                ));
        when(fixture.documentRepository.countByProjectIdAndAuthorIdAndTypeNot(
                fixture.projectId, fixture.first.getId(), DocumentType.DESIGN)).thenReturn(1L);
        when(fixture.documentRepository.countByProjectIdAndAuthorIdAndType(
                fixture.projectId, fixture.second.getId(), DocumentType.DESIGN)).thenReturn(1L);
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.first.getId())).thenReturn(3L);
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.second.getId())).thenReturn(1L);

        ProjectContributionCalculation first = fixture.service.calculate(
                fixture.projectId,
                Map.of(fixture.first.getId(), new BigDecimal("60"))
        );
        ProjectContributionCalculation repeated = fixture.service.calculate(
                fixture.projectId,
                Map.of(fixture.first.getId(), new BigDecimal("60"))
        );

        ContributionBreakdown firstStudent = breakdown(first, fixture.first);
        ContributionBreakdown secondStudent = breakdown(first, fixture.second);
        ContributionBreakdown thirdStudent = breakdown(first, fixture.third);
        assertThat(firstStudent.codeScore()).isEqualByComparingTo("2");
        assertThat(firstStudent.documentScore()).isEqualByComparingTo("1");
        assertThat(secondStudent.designScore()).isEqualByComparingTo("2");
        assertThat(firstStudent.adjustedSprintScore()).isEqualByComparingTo("3");
        assertThat(secondStudent.adjustedSprintScore()).isEqualByComparingTo("1");
        assertThat(firstStudent.peerCoefficient()).isEqualByComparingTo("1");
        assertThat(firstStudent.codeContributionPercent())
                .isCloseTo(new BigDecimal("100"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.00000000000001")));
        assertThat(firstStudent.taskContributionPercent()).isEqualByComparingTo("75");
        assertThat(firstStudent.rawContribution()).isEqualByComparingTo(firstStudent.adjustedContribution());
        assertThat(firstStudent.finalContribution()).isEqualByComparingTo("60");
        assertThat(secondStudent.finalContribution()).isEqualByComparingTo("40");
        assertThat(thirdStudent.finalContribution()).isEqualByComparingTo("0");
        assertThat(repeated).isEqualTo(first);
    }

    @Test
    void appliesPeerReviewRatioToSprintAndProjectCoefficient() {
        Fixture fixture = fixture();
        PeerReview firstReview = PeerReview.builder()
                .starRating(4)
                .reviewee(fixture.first)
                .sprint(fixture.sprint)
                .build();
        PeerReview secondReview = PeerReview.builder()
                .starRating(1)
                .reviewee(fixture.second)
                .sprint(fixture.sprint)
                .build();
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.first.getId())).thenReturn(3L);
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.second.getId())).thenReturn(1L);
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                any(), eq(fixture.projectId)))
                .thenReturn(List.of(firstReview, secondReview));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of(
                        fixture.first.getId(), new BigDecimal("100"),
                        fixture.second.getId(), BigDecimal.ZERO,
                        fixture.third.getId(), BigDecimal.ZERO
                )),
                fixture.first
        );

        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("2.4");
        assertThat(result.peerCoefficient()).isEqualByComparingTo("0.8");
        assertThat(result.adjustedContribution())
                .isEqualByComparingTo(result.rawContribution().multiply(new BigDecimal("0.8")));
    }

    @Test
    void returnsZeroSourcePercentagesWhenEverySourceScoreIsZero() {
        Fixture fixture = fixture();
        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of(
                fixture.first.getId(), new BigDecimal("100"),
                fixture.second.getId(), BigDecimal.ZERO,
                fixture.third.getId(), BigDecimal.ZERO
        ));

        for (ContributionBreakdown student : result.students()) {
            assertThat(student.codeContributionPercent()).isEqualByComparingTo("0");
            assertThat(student.documentContributionPercent()).isEqualByComparingTo("0");
            assertThat(student.designContributionPercent()).isEqualByComparingTo("0");
            assertThat(student.taskContributionPercent()).isEqualByComparingTo("0");
        }
    }

    @Test
    void classifiesDoneTasksIntoCodeDocumentAndDesignSlicesUsingJiraMetadata() {
        Fixture fixture = fixture();
        Project project = new Project();
        project.setId(fixture.projectId);
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());
        Task designTask = new Task();
        designTask.setProject(project);
        designTask.setSprint(sprint);
        designTask.setAssignee(fixture.first());
        designTask.setStatus(TaskStatus.DONE);
        designTask.setStoryPoint(4);
        designTask.setType(TaskType.TASK);
        designTask.setLabels(List.of("ui-ux", "design"));
        designTask.setDescription("Create new dashboard prototype");

        Task docTask = new Task();
        docTask.setProject(project);
        docTask.setSprint(sprint);
        docTask.setAssignee(fixture.first());
        docTask.setStatus(TaskStatus.DONE);
        docTask.setStoryPoint(3);
        docTask.setType(TaskType.STORY);
        docTask.setLabels(List.of("documentation"));
        docTask.setDescription("Write API spec for onboarding");

        Task codeTask = new Task();
        codeTask.setProject(project);
        codeTask.setSprint(sprint);
        codeTask.setAssignee(fixture.first());
        codeTask.setStatus(TaskStatus.DONE);
        codeTask.setStoryPoint(5);
        codeTask.setType(TaskType.FEATURE);
        codeTask.setLabels(List.of("backend"));
        codeTask.setDescription("Implement contribution engine");

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(designTask, docTask, codeTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.codeScore()).isEqualByComparingTo("5");
        assertThat(first.documentScore()).isEqualByComparingTo("3");
        assertThat(first.designScore()).isEqualByComparingTo("4");
        assertThat(first.adjustedSprintScore()).isEqualByComparingTo("12");
    }

    @Test
    void classifiesFrontendAndBugFixTasksAsCodeInsteadOfDesign() {
        Fixture fixture = fixture();
        Project project = new Project();
        project.setId(fixture.projectId);
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());

        Task frontendTask = new Task();
        frontendTask.setProject(project);
        frontendTask.setSprint(sprint);
        frontendTask.setAssignee(fixture.first());
        frontendTask.setStatus(TaskStatus.DONE);
        frontendTask.setStoryPoint(2);
        frontendTask.setType(TaskType.TASK);
        frontendTask.setLabels(List.of("frontend", "ui"));
        frontendTask.setDescription("Implement checkout screen with new components");

        Task bugFixTask = new Task();
        bugFixTask.setProject(project);
        bugFixTask.setSprint(sprint);
        bugFixTask.setAssignee(fixture.first());
        bugFixTask.setStatus(TaskStatus.DONE);
        bugFixTask.setStoryPoint(3);
        bugFixTask.setType(TaskType.BUG);
        bugFixTask.setLabels(List.of("bugfix", "backend"));
        bugFixTask.setDescription("Fix login flow regression");

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(frontendTask, bugFixTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.codeScore()).isEqualByComparingTo("5");
        assertThat(first.documentScore()).isEqualByComparingTo("0");
        assertThat(first.designScore()).isEqualByComparingTo("0");
        assertThat(first.adjustedSprintScore()).isEqualByComparingTo("5");
    }

    @Test
    void normalizesOverridesAboveOneHundredWithoutDividingZeroBase() {
        Fixture fixture = fixture();
        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of(
                fixture.first.getId(), new BigDecimal("80"),
                fixture.second.getId(), new BigDecimal("80")
        ));

        assertThat(breakdown(result, fixture.first).finalContribution()).isEqualByComparingTo("50");
        assertThat(breakdown(result, fixture.second).finalContribution()).isEqualByComparingTo("50");
        assertThat(breakdown(result, fixture.third).finalContribution()).isEqualByComparingTo("0");
    }

    @Test
    void defaultsPeerCoefficientToOneWhenNoPeerReviewsExist() {
        Fixture fixture = fixture();
        when(fixture.documentRepository.countByProjectIdAndAuthorIdAndTypeNot(
                fixture.projectId, fixture.first.getId(), DocumentType.DESIGN)).thenReturn(1L);
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.first.getId())).thenReturn(3L);
        ContributionBreakdown result = breakdown(fixture.service.calculate(fixture.projectId, Map.of()), fixture.first);

        assertThat(result.peerCoefficient()).isEqualByComparingTo("1");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("3");
    }

    @Test
    void splitsRemainingBudgetEquallyWhenOverridesLeaveNoEvidenceForOthers() {
        Fixture fixture = fixture();

        ProjectContributionCalculation result = fixture.service.calculate(
                fixture.projectId,
                Map.of(fixture.first.getId(), new BigDecimal("40"))
        );

        assertThat(breakdown(result, fixture.first).finalContribution()).isEqualByComparingTo("40");
        assertThat(breakdown(result, fixture.second).finalContribution()).isEqualByComparingTo("30");
        assertThat(breakdown(result, fixture.third).finalContribution()).isEqualByComparingTo("30");
    }

    @Test
    void normalizesAllStudentOverridesToOneHundredWhenTheyUndershoot() {
        Fixture fixture = fixture();

        ProjectContributionCalculation result = fixture.service.calculate(
                fixture.projectId,
                Map.of(
                        fixture.first.getId(), new BigDecimal("20"),
                        fixture.second.getId(), new BigDecimal("30"),
                        fixture.third.getId(), BigDecimal.ZERO
                )
        );

        assertThat(breakdown(result, fixture.first).finalContribution()).isEqualByComparingTo("40");
        assertThat(breakdown(result, fixture.second).finalContribution()).isEqualByComparingTo("60");
        assertThat(breakdown(result, fixture.third).finalContribution()).isEqualByComparingTo("0");
    }

    private ContributionBreakdown breakdown(
            ProjectContributionCalculation calculation,
            Student student
    ) {
        return calculation.students().stream()
                .filter(item -> item.studentId().equals(student.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Fixture fixture() {
        UUID projectId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Subject subject = new Subject();
        subject.setId(subjectId);
        Course course = new Course();
        course.setSubject(subject);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        Student first = student();
        Student second = student();
        Student third = student();
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());

        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        CommitDataRepository commitRepository = mock(CommitDataRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        SprintRepository sprintRepository = mock(SprintRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        PeerReviewRepository peerReviewRepository = mock(PeerReviewRepository.class);
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(team.getId())).thenReturn(List.of(
                TeamMember.builder().team(team).student(first).build(),
                TeamMember.builder().team(team).student(second).build(),
                TeamMember.builder().team(team).student(third).build()
        ));
        when(sprintRepository.findByBoardProjectId(projectId)).thenReturn(List.of(sprint));
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(projectId)))
                .thenReturn(List.of());

        return new Fixture(
                projectId, subjectId, first, second, third, sprint, commitRepository,
                documentRepository, taskRepository, peerReviewRepository,
                new ContributionCalculationService(
                        teamRepository, teamMemberRepository, commitRepository, documentRepository,
                        sprintRepository, taskRepository, peerReviewRepository
                )
        );
    }

    private Student student() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        return student;
    }

    private CommitData commitWithTask(Task task) {
        CommitData commit = new CommitData();
        commit.setTask(task);
        return commit;
    }

    private record Fixture(
            UUID projectId,
            UUID subjectId,
            Student first,
            Student second,
            Student third,
            Sprint sprint,
            CommitDataRepository commitRepository,
            DocumentRepository documentRepository,
            TaskRepository taskRepository,
            PeerReviewRepository peerReviewRepository,
            ContributionCalculationService service
    ) {
    }
}
