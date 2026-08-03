package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PeerReviewConfig;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.PeerReviewConfigRepository;
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
        when(fixture.commitRepository.countByProjectIdAndAuthorId(
                fixture.projectId, fixture.first.getId())).thenReturn(2L);
        when(fixture.commitRepository.countByProjectIdAndAuthorId(
                fixture.projectId, fixture.second.getId())).thenReturn(1L);
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
        assertThat(secondStudent.designScore()).isEqualByComparingTo("1");
        assertThat(firstStudent.adjustedSprintScore()).isEqualByComparingTo("3");
        assertThat(secondStudent.adjustedSprintScore()).isEqualByComparingTo("1");
        assertThat(firstStudent.peerCoefficient()).isEqualByComparingTo("1");
        assertThat(firstStudent.codeContributionPercent())
                .isCloseTo(new BigDecimal("66.66666666666667"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.00000000000001")));
        assertThat(firstStudent.taskContributionPercent()).isEqualByComparingTo("75");
        assertThat(firstStudent.rawContribution()).isEqualByComparingTo(firstStudent.adjustedContribution());
        assertThat(firstStudent.finalContribution()).isEqualByComparingTo("60");
        assertThat(secondStudent.finalContribution()).isEqualByComparingTo("40");
        assertThat(thirdStudent.finalContribution()).isEqualByComparingTo("0");
        assertThat(repeated).isEqualTo(first);
    }

    @Test
    void appliesConfiguredPeerReviewMultiplierToSprintAndProjectCoefficient() {
        Fixture fixture = fixture();
        PeerReview review = PeerReview.builder().starRating(5).build();
        PeerReviewConfig config = PeerReviewConfig.builder().starRating(5).multiplier(1.2F).build();
        when(fixture.taskRepository.sumDoneEffectiveStoryPoints(
                fixture.projectId, fixture.sprint.getId(), fixture.first.getId())).thenReturn(3L);
        when(fixture.peerReviewRepository.findByRevieweeIdAndSprintId(
                fixture.first.getId(), fixture.sprint.getId())).thenReturn(List.of(review));
        when(fixture.peerReviewRepository.findByRevieweeIdAndSprintBoardProjectId(
                fixture.first.getId(), fixture.projectId)).thenReturn(List.of(review));
        when(fixture.peerReviewConfigRepository.findApplicableBySubjectIdAndStarRating(
                fixture.subjectId, 5)).thenReturn(List.of(config));

        ContributionBreakdown result = breakdown(
                fixture.service.calculate(fixture.projectId, Map.of(
                        fixture.first.getId(), new BigDecimal("100"),
                        fixture.second.getId(), BigDecimal.ZERO,
                        fixture.third.getId(), BigDecimal.ZERO
                )),
                fixture.first
        );

        assertThat(result.adjustedSprintScore()).isEqualByComparingTo("3.6");
        assertThat(result.peerCoefficient()).isEqualByComparingTo("1.2");
        assertThat(result.adjustedContribution())
                .isEqualByComparingTo(result.rawContribution().multiply(new BigDecimal("1.2")));
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
    void failsClosedWhenSubjectAndDefaultConfigsMakePrecedenceAmbiguous() {
        Fixture fixture = fixture();
        PeerReview review = PeerReview.builder().starRating(5).build();
        when(fixture.peerReviewRepository.findByRevieweeIdAndSprintId(
                fixture.first.getId(), fixture.sprint.getId())).thenReturn(List.of(review));
        when(fixture.peerReviewConfigRepository.findApplicableBySubjectIdAndStarRating(
                fixture.subjectId, 5)).thenReturn(List.of(
                        PeerReviewConfig.builder().starRating(5).multiplier(1.1F).build(),
                        PeerReviewConfig.builder().starRating(5).multiplier(1.2F).build()
                ));

        assertThatThrownBy(() -> fixture.service.calculate(fixture.projectId, Map.of()))
                .isInstanceOf(ContributionCalculationException.class)
                .hasMessageContaining("precedence");
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
        PeerReviewConfigRepository peerReviewConfigRepository = mock(PeerReviewConfigRepository.class);
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(team.getId())).thenReturn(List.of(
                TeamMember.builder().team(team).student(first).build(),
                TeamMember.builder().team(team).student(second).build(),
                TeamMember.builder().team(team).student(third).build()
        ));
        when(sprintRepository.findByBoardProjectId(projectId)).thenReturn(List.of(sprint));
        when(peerReviewRepository.findByRevieweeIdAndSprintId(any(UUID.class), any(UUID.class)))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdAndSprintBoardProjectId(any(UUID.class), any(UUID.class)))
                .thenReturn(List.of());

        return new Fixture(
                projectId, subjectId, first, second, third, sprint, commitRepository,
                documentRepository, taskRepository, peerReviewRepository,
                peerReviewConfigRepository,
                new ContributionCalculationService(
                        teamRepository, teamMemberRepository, commitRepository, documentRepository,
                        sprintRepository, taskRepository, peerReviewRepository,
                        peerReviewConfigRepository
                )
        );
    }

    private Student student() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        return student;
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
            PeerReviewConfigRepository peerReviewConfigRepository,
            ContributionCalculationService service
    ) {
    }
}
