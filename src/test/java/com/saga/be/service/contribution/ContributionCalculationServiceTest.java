package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskAttachment;
import com.saga.be.entity.TaskWebLink;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskAttachmentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TaskWebLinkRepository;
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
        // Product decision: Task is the sole numeric Contribution authority — evidence must come
        // from genuinely DONE+assigned Tasks (via taskRepository.findByProjectIdAndAssigneeId),
        // not from commit links, which never mint additional score of their own.
        Fixture fixture = fixture();
        Task firstCodeTask = new Task();
        firstCodeTask.setStatus(TaskStatus.DONE);
        firstCodeTask.setAssignee(fixture.first);
        firstCodeTask.setSprint(fixture.sprint);
        firstCodeTask.setStoryPoint(1);
        firstCodeTask.setLabels(List.of("saga:code"));
        Task secondCodeTask = new Task();
        secondCodeTask.setStatus(TaskStatus.DONE);
        secondCodeTask.setAssignee(fixture.first);
        secondCodeTask.setSprint(fixture.sprint);
        secondCodeTask.setStoryPoint(1);
        secondCodeTask.setLabels(List.of("saga:code"));
        Task secondDesignTask = new Task();
        secondDesignTask.setStatus(TaskStatus.DONE);
        secondDesignTask.setAssignee(fixture.second);
        secondDesignTask.setSprint(fixture.sprint);
        secondDesignTask.setStoryPoint(2);
        secondDesignTask.setLabels(List.of("saga:document"));
        secondDesignTask.setId(UUID.randomUUID());
        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(TaskAttachment.builder().task(secondDesignTask).externalId("doc-1").build()));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(firstCodeTask, secondCodeTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of(secondDesignTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(
                        review(fixture, fixture.first, 1),
                        review(fixture, fixture.second, 1)
                ));

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
        assertThat(firstStudent.documentScore()).isEqualByComparingTo("0");
        assertThat(secondStudent.documentScore()).isEqualByComparingTo("2");
        assertThat(firstStudent.adjustedSprintScore()).isEqualByComparingTo("1");
        assertThat(secondStudent.adjustedSprintScore()).isEqualByComparingTo("1");
        assertThat(firstStudent.peerCoefficient()).isEqualByComparingTo("0.5");
        assertThat(firstStudent.codeContributionPercent())
                .isCloseTo(new BigDecimal("100"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.00000000000001")));
        assertThat(firstStudent.taskContributionPercent()).isEqualByComparingTo("50");
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
            assertThat(student.researchContributionPercent()).isEqualByComparingTo("0");
            assertThat(student.taskContributionPercent()).isEqualByComparingTo("0");
        }
    }

    @Test
    void classifiesDoneTasksIntoCodeAndDocumentSlicesUsingJiraMetadata() {
        Fixture fixture = fixture();
        Project project = new Project();
        project.setId(fixture.projectId);
        Task designTask = new Task();
        designTask.setProject(project);
        designTask.setSprint(fixture.sprint);
        designTask.setAssignee(fixture.first());
        designTask.setStatus(TaskStatus.DONE);
        designTask.setStoryPoint(4);
        designTask.setType(TaskType.TASK);
        designTask.setLabels(List.of("saga:document"));
        designTask.setDescription("Create new dashboard prototype");

        Task docTask = new Task();
        docTask.setProject(project);
        docTask.setSprint(fixture.sprint);
        docTask.setAssignee(fixture.first());
        docTask.setStatus(TaskStatus.DONE);
        docTask.setStoryPoint(3);
        docTask.setType(TaskType.STORY);
        docTask.setLabels(List.of("saga:document"));
        docTask.setDescription("Write API spec for onboarding");

        Task codeTask = new Task();
        codeTask.setProject(project);
        codeTask.setSprint(fixture.sprint);
        codeTask.setAssignee(fixture.first());
        codeTask.setStatus(TaskStatus.DONE);
        codeTask.setStoryPoint(5);
        codeTask.setType(TaskType.FEATURE);
        codeTask.setLabels(List.of("saga:code"));
        codeTask.setDescription("Implement contribution engine");
        designTask.setId(UUID.randomUUID());
        docTask.setId(UUID.randomUUID());
        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(
                        TaskAttachment.builder().task(designTask).externalId("design-1").build(),
                        TaskAttachment.builder().task(docTask).externalId("doc-1").build()
                ));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(designTask, docTask, codeTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.codeScore()).isEqualByComparingTo("5");
        assertThat(first.documentScore()).isEqualByComparingTo("7");
        assertThat(first.adjustedSprintScore()).isEqualByComparingTo("12");
    }

    @Test
    void tasksWithoutReservedMarkerDoNotEnterAnyCriterion() {
        Fixture fixture = fixture();
        Project project = new Project();
        project.setId(fixture.projectId);
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());

        Task frontendTask = new Task();
        frontendTask.setProject(project);
        frontendTask.setSprint(fixture.sprint);
        frontendTask.setAssignee(fixture.first());
        frontendTask.setStatus(TaskStatus.DONE);
        frontendTask.setStoryPoint(2);
        frontendTask.setType(TaskType.TASK);
        frontendTask.setLabels(List.of("frontend", "ui"));
        frontendTask.setDescription("Implement checkout screen with new components");

        Task bugFixTask = new Task();
        bugFixTask.setProject(project);
        bugFixTask.setSprint(fixture.sprint);
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
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.codeScore()).isEqualByComparingTo("0");
        assertThat(first.documentScore()).isEqualByComparingTo("0");
        assertThat(first.adjustedSprintScore()).isEqualByComparingTo("5");
    }

    @Test
    void documentAndResearchStoryPointsCountOnlyWhenTheTaskHasAnAttachment() {
        Fixture fixture = fixture();
        UUID documentTaskId = UUID.randomUUID();
        UUID researchTaskId = UUID.randomUUID();
        UUID codeTaskId = UUID.randomUUID();
        Task documentTask = markedDoneTask(3, "saga:document");
        documentTask.setId(documentTaskId);
        documentTask.setSprint(fixture.sprint);
        Task researchWithoutFile = markedDoneTask(2, "saga:research");
        researchWithoutFile.setId(researchTaskId);
        researchWithoutFile.setSprint(fixture.sprint);
        Task codeTask = markedDoneTask(4, "saga:code");
        codeTask.setId(codeTaskId);
        codeTask.setSprint(fixture.sprint);

        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(
                        TaskAttachment.builder().task(documentTask).externalId("a1").build(),
                        TaskAttachment.builder().task(documentTask).externalId("a2").build(),
                        TaskAttachment.builder().task(codeTask).externalId("a4").build()
                ));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(documentTask, researchWithoutFile, codeTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of()), fixture.first
        );

        assertThat(result.documentScore()).isEqualByComparingTo("3");
        assertThat(result.researchScore()).isEqualByComparingTo("0");
        assertThat(result.codeScore()).isEqualByComparingTo("4");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("9");
    }

    @Test
    void documentStoryPointsCountWhenTheTaskHasOnlyASubmittedLink() {
        Fixture fixture = fixture();
        Task documentTask = markedDoneTask(3, "saga:document");
        documentTask.setId(UUID.randomUUID());
        documentTask.setSprint(fixture.sprint);
        when(fixture.taskWebLinkRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(
                        TaskWebLink.builder()
                                .task(documentTask)
                                .url("https://docs.google.com/document/d/abc")
                                .build()
                ));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(documentTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of()), fixture.first
        );

        assertThat(result.documentScore()).isEqualByComparingTo("3");
        assertThat(result.researchScore()).isEqualByComparingTo("0");
        assertThat(result.codeScore()).isEqualByComparingTo("0");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("3");
    }

    @Test
    void exactSagaTestMarkerRoutesTaskToTestRegardlessOfOtherLabels() {
        Fixture fixture = fixture();
        Task markedTask = new Task();
        markedTask.setAssignee(fixture.first());
        markedTask.setSprint(fixture.sprint);
        markedTask.setStatus(TaskStatus.DONE);
        markedTask.setStoryPoint(5);
        markedTask.setLabels(List.of("saga:test", "design-review-needed"));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(markedTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.testScore()).isEqualByComparingTo("5");
        assertThat(first.codeScore()).isEqualByComparingTo("0");
        assertThat(first.documentScore()).isEqualByComparingTo("0");
        assertThat(first.researchScore()).isEqualByComparingTo("0");
    }

    @Test
    void exactCodeDocumentAndResearchMarkersRouteFullStoryPointsToTheirOwnCriteria() {
        Fixture fixture = fixture();
        Task codeTask = markedDoneTask(2, "saga:code", "documentation");
        codeTask.setSprint(fixture.sprint);
        Task documentTask = markedDoneTask(3, "saga:document", "backend");
        documentTask.setId(UUID.randomUUID());
        documentTask.setSprint(fixture.sprint);
        Task researchTask = markedDoneTask(4, "saga:research", "backend");
        researchTask.setId(UUID.randomUUID());
        researchTask.setSprint(fixture.sprint);
        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(
                        TaskAttachment.builder().task(documentTask).externalId("doc-1").build(),
                        TaskAttachment.builder().task(researchTask).externalId("res-1").build()
                ));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(codeTask, documentTask, researchTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of()), fixture.first
        );

        assertThat(result.codeScore()).isEqualByComparingTo("2");
        assertThat(result.testScore()).isEqualByComparingTo("0");
        assertThat(result.documentScore()).isEqualByComparingTo("3");
        assertThat(result.researchScore()).isEqualByComparingTo("4");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("9");
    }

    @Test
    void nullStoryPointFallsBackToOneWithoutChangingMarkerRouting() {
        Fixture fixture = fixture();
        Task researchTask = markedDoneTask(null, "saga:research");
        researchTask.setId(UUID.randomUUID());
        researchTask.setSprint(fixture.sprint);
        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(TaskAttachment.builder().task(researchTask).externalId("res-1").build()));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(researchTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of()), fixture.first
        );

        assertThat(result.researchScore()).isEqualByComparingTo("1");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("1");
        assertThat(result.codeScore()).isEqualByComparingTo("0");
        assertThat(result.testScore()).isEqualByComparingTo("0");
        assertThat(result.documentScore()).isEqualByComparingTo("0");
    }

    @Test
    void nonDoneTaskWithReservedMarkerDoesNotMintCriterionOrNumericScore() {
        Fixture fixture = fixture();
        Task inProgressTask = new Task();
        inProgressTask.setAssignee(fixture.first());
        inProgressTask.setStatus(TaskStatus.IN_PROGRESS);
        inProgressTask.setStoryPoint(8);
        inProgressTask.setLabels(List.of("saga:test"));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(inProgressTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of()), fixture.first
        );

        assertThat(result.codeScore()).isEqualByComparingTo("0");
        assertThat(result.testScore()).isEqualByComparingTo("0");
        assertThat(result.documentScore()).isEqualByComparingTo("0");
        assertThat(result.researchScore()).isEqualByComparingTo("0");
        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("0");
    }

    @Test
    void conflictingReservedMarkersExcludeTheTaskFromEveryCriterionButStillCountTowardTaskScore() {
        Fixture fixture = fixture();
        Task ambiguousTask = new Task();
        ambiguousTask.setAssignee(fixture.first());
        ambiguousTask.setSprint(fixture.sprint);
        ambiguousTask.setStatus(TaskStatus.DONE);
        ambiguousTask.setStoryPoint(5);
        ambiguousTask.setLabels(List.of("saga:test", "saga:research"));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(ambiguousTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of());
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(review(fixture, fixture.first, 1)));

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());
        ContributionBreakdown first = breakdown(result, fixture.first);

        assertThat(first.codeScore()).isEqualByComparingTo("0");
        assertThat(first.testScore()).isEqualByComparingTo("0");
        assertThat(first.documentScore()).isEqualByComparingTo("0");
        assertThat(first.researchScore()).isEqualByComparingTo("0");
        // The numeric task/sprint formula is unchanged: the ambiguous Task's storyPoint still
        // counts toward adjustedSprintScore even though it is excluded from all four criteria.
        assertThat(first.adjustedSprintScore()).isEqualByComparingTo("5");
    }

    @Test
    void calculateIgnoresHistoricalProjectGroupWeightConfigAndUsesCourseWeightsOnly() {
        Fixture fixture = fixture();
        Task codeTask = new Task();
        codeTask.setStatus(TaskStatus.DONE);
        codeTask.setSprint(fixture.sprint);
        codeTask.setStoryPoint(1);
        codeTask.setLabels(List.of("saga:code"));
        Task designTask = new Task();
        designTask.setStatus(TaskStatus.DONE);
        designTask.setSprint(fixture.sprint);
        designTask.setStoryPoint(1);
        designTask.setLabels(List.of("saga:document"));
        designTask.setId(UUID.randomUUID());
        when(fixture.taskAttachmentRepository.findByTask_Project_Id(fixture.projectId))
                .thenReturn(List.of(TaskAttachment.builder().task(designTask).externalId("doc-1").build()));

        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.first.getId()))
                .thenReturn(List.of(codeTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.second.getId()))
                .thenReturn(List.of(designTask));
        when(fixture.taskRepository.findByProjectIdAndAssigneeId(fixture.projectId, fixture.third.getId()))
                .thenReturn(List.of());
        when(fixture.peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(any(), eq(fixture.projectId)))
                .thenReturn(List.of(
                        review(fixture, fixture.first, 1),
                        review(fixture, fixture.second, 1)
                ));
        // Historical row still exists in the DB but the resolver has no dependency on this
        // repository, so it is structurally impossible for it to change the result.
        when(fixture.projectGroupWeightConfigRepository.findByProjectId(fixture.projectId))
                .thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                        .team(fixture.team)
                        .codeWeight(new BigDecimal("0.2"))
                        .documentWeight(new BigDecimal("0.3"))
                        .designWeight(new BigDecimal("0.5"))
                        .build()));

        ProjectContributionCalculation result = fixture.service.calculate(fixture.projectId, Map.of());

        assertThat(breakdown(result, fixture.first).finalContribution())
                .isCloseTo(new BigDecimal("50"), org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
        assertThat(breakdown(result, fixture.second).finalContribution())
                .isCloseTo(new BigDecimal("50"), org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
        assertThat(breakdown(result, fixture.third).finalContribution()).isEqualByComparingTo("0");
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

    private PeerReview review(Fixture fixture, Student reviewee, int stars) {
        return PeerReview.builder()
                .starRating(stars)
                .reviewee(reviewee)
                .sprint(fixture.sprint)
                .build();
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
        Project project = new Project();
        project.setId(projectId);
        project.setCourse(course);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        team.setProject(project);
        Student first = student();
        Student second = student();
        Student third = student();
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());

        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        SprintRepository sprintRepository = mock(SprintRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        PeerReviewRepository peerReviewRepository = mock(PeerReviewRepository.class);
        ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository =
                mock(ProjectGroupWeightConfigRepository.class);
        TaskAttachmentRepository taskAttachmentRepository = mock(TaskAttachmentRepository.class);
        TaskWebLinkRepository taskWebLinkRepository = mock(TaskWebLinkRepository.class);
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
                projectId, subjectId, team, first, second, third, sprint,
                taskRepository, peerReviewRepository, projectGroupWeightConfigRepository,
                taskAttachmentRepository, taskWebLinkRepository,
                new ContributionCalculationService(
                        teamRepository, teamMemberRepository,
                        sprintRepository, taskRepository, peerReviewRepository,
                        new ContributionSliceWeightResolver(projectGroupWeightConfigRepository),
                        taskAttachmentRepository,
                        taskWebLinkRepository
                )
        );
    }

    private Student student() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        return student;
    }

    private Task markedDoneTask(Integer storyPoints, String... labels) {
        Task task = new Task();
        task.setStatus(TaskStatus.DONE);
        task.setStoryPoint(storyPoints);
        task.setLabels(List.of(labels));
        return task;
    }

    private record Fixture(
            UUID projectId,
            UUID subjectId,
            Team team,
            Student first,
            Student second,
            Student third,
            Sprint sprint,
            TaskRepository taskRepository,
            PeerReviewRepository peerReviewRepository,
            ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            TaskWebLinkRepository taskWebLinkRepository,
            ContributionCalculationService service
    ) {
    }
}
