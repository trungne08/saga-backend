package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionGraphResponse;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskAttachment;
import com.saga.be.entity.TaskWebLink;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.PolicyOverrideRequestRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskAttachmentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TaskWebLinkRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.contribution.ContributionSliceWeightResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TeamContributionServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CommitDataRepository commitDataRepository;

    @Mock
    private PeerReviewRepository peerReviewRepository;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private PolicyOverrideRequestRepository policyOverrideRequestRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    @Mock
    private TaskAttachmentRepository taskAttachmentRepository;

    @Mock
    private TaskWebLinkRepository taskWebLinkRepository;

    private TeamContributionService service;

    @BeforeEach
    void setUp() {
        service = new TeamContributionService(
                teamRepository,
                teamMemberRepository,
                taskRepository,
                commitDataRepository,
                peerReviewRepository,
                sprintRepository,
                policyOverrideRequestRepository,
                lecturerRepository,
                new ContributionSliceWeightResolver(projectGroupWeightConfigRepository),
                taskAttachmentRepository,
                taskWebLinkRepository
        );
    }

    @Test
    void evaluatesContributionWithPeerCoefficientAndWarnings() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        UUID studentOneId = UUID.randomUUID();
        UUID studentTwoId = UUID.randomUUID();

        Subject subject = entityWithId(new Subject(), subjectId);
        subject.setName("Software Engineering");

        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setSubject(subject);

        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);

        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        Student studentOne = entityWithId(new Student(), studentOneId);
        studentOne.setFullName("Alice Nguyen");
        studentOne.setStudentCode("SE001");

        Student studentTwo = entityWithId(new Student(), studentTwoId);
        studentTwo.setFullName("Bob Tran");
        studentTwo.setStudentCode("SE002");

        TeamMember memberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        memberOne.setTeam(team);
        memberOne.setStudent(studentOne);
        memberOne.setRoleInTeam(RoleInTeam.LEADER);

        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(studentTwo);
        memberTwo.setRoleInTeam(RoleInTeam.MEMBER);

        Sprint sprint = entityWithId(new Sprint(), sprintId);
        sprint.setName("Sprint 1");

        Task taskOne = entityWithId(new Task(), UUID.randomUUID());
        taskOne.setProject(project);
        taskOne.setSprint(sprint);
        taskOne.setAssignee(studentOne);
        taskOne.setStatus(TaskStatus.DONE);
        taskOne.setStoryPoint(3);
        taskOne.setTitle("Implement login");
        taskOne.setExternalKey("SAGA-1");
        taskOne.setLabels(List.of("saga:code"));

        Task taskTwo = entityWithId(new Task(), UUID.randomUUID());
        taskTwo.setProject(project);
        taskTwo.setSprint(sprint);
        taskTwo.setAssignee(studentTwo);
        taskTwo.setStatus(TaskStatus.DONE);
        taskTwo.setStoryPoint(5);
        taskTwo.setTitle("Implement signup");
        taskTwo.setExternalKey("SAGA-2");
        taskTwo.setLabels(List.of("saga:code"));

        PeerReview review = new PeerReview();
        review.setSprint(sprint);
        review.setReviewer(studentTwo);
        review.setReviewee(studentOne);
        review.setStarRating(4);

        PeerReview reviewTwo = new PeerReview();
        reviewTwo.setSprint(sprint);
        reviewTwo.setReviewer(studentOne);
        reviewTwo.setReviewee(studentTwo);
        reviewTwo.setStarRating(1);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(taskOne, taskTwo));
        Task linkedCodeTask = new Task();
        linkedCodeTask.setStoryPoint(1);
        linkedCodeTask.setLabels(List.of("saga:code"));
        Task linkedSecondCodeTask = new Task();
        linkedSecondCodeTask.setStoryPoint(1);
        linkedSecondCodeTask.setLabels(List.of("saga:code"));
        Task linkedDesignTask = new Task();
        linkedDesignTask.setStoryPoint(1);
        linkedDesignTask.setLabels(List.of("saga:document"));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(
                        commitWithTask(linkedCodeTask),
                        commitWithTask(linkedCodeTask),
                        commitWithTask(linkedCodeTask)
                ));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of(commitWithTask(linkedDesignTask)));
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of(review, reviewTwo));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));

        TeamContributionEvaluationResponse response = service.evaluate(teamId);

        assertEquals(teamId, response.teamId());
        assertEquals(2, response.members().size());

        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(studentOneId))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(studentTwoId))
                .findFirst()
                .orElseThrow();

        // Product decision: when a commit is linked to a Task, the Task is the sole numeric
        // Contribution authority — the three commits linked to linkedCodeTask/linkedDesignTask
        // below never mint additional score (they are supporting/provenance evidence only).
        // Only taskOne(storyPoint=3, saga:code) and taskTwo(storyPoint=5, saga:code)
        // drive codeScore. Neither task has DOCUMENT evidence, so totalDocument = 0.
        assertEquals(37.5, alice.codeContributionScore(), 0.0001);
        assertEquals(37.5, alice.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, alice.documentContributionPercentage(), 0.0001);
        assertEquals(70.5882, alice.taskContributionScore(), 0.0001);
        assertEquals(0.8, alice.peerReviewScore(), 0.0001);
        assertEquals(0.75, alice.sliceScore(), 0.0001);
        assertEquals(37.5, alice.sliceContributionPercentage(), 0.0001);
        assertEquals(70.5882, alice.finalContributionPercentage(), 0.001);
        assertEquals(3, alice.evidenceCount());

        assertEquals(62.5, bob.codeContributionScore(), 0.0001);
        assertEquals(62.5, bob.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, bob.documentContributionPercentage(), 0.0001);
        assertEquals(29.4118, bob.taskContributionScore(), 0.0001);
        assertEquals(0.2, bob.peerReviewScore(), 0.0001);
        assertEquals(1.25, bob.sliceScore(), 0.0001);
        assertEquals(62.5, bob.sliceContributionPercentage(), 0.0001);
        assertEquals(29.4118, bob.finalContributionPercentage(), 0.001);
        assertEquals(1, alice.sprintBreakdowns().size());
        assertEquals(0.75, alice.sprintBreakdowns().get(0).sliceScore(), 0.0001);
        assertEquals(37.5, alice.sprintBreakdowns().get(0).sliceContributionPercentage(), 0.0001);
        assertEquals(70.5882, alice.sprintBreakdowns().get(0).contributionPercentage(), 0.001);
        assertEquals(3.0, alice.sprintBreakdowns().get(0).codeStoryPoints(), 0.0001);
        assertEquals(0.0, alice.sprintBreakdowns().get(0).testStoryPoints(), 0.0001);
        assertEquals(0.0, alice.sprintBreakdowns().get(0).documentStoryPoints(), 0.0001);
        assertEquals(0.0, alice.sprintBreakdowns().get(0).researchStoryPoints(), 0.0001);
        assertEquals(1.25, bob.sprintBreakdowns().get(0).sliceScore(), 0.0001);
        assertEquals(62.5, bob.sprintBreakdowns().get(0).sliceContributionPercentage(), 0.0001);
        assertEquals(29.4118, bob.sprintBreakdowns().get(0).contributionPercentage(), 0.001);
        assertEquals(5.0, bob.sprintBreakdowns().get(0).codeStoryPoints(), 0.0001);

        TeamContributionGraphResponse graph = service.graph(teamId);
        assertNull(graph.sprintId());
        assertNull(graph.sprintName());
        assertEquals(TeamContributionGraphResponse.FORMULA, graph.formula());
        assertEquals(0.25, graph.weights().codeWeightRatio(), 0.0001);
        assertEquals(25.0, graph.weights().codeWeightPercent(), 0.0001);
        assertEquals(6, graph.nodes().size());
        assertEquals(2, graph.edges().size());
        assertTrue(graph.nodes().stream().noneMatch(node -> "DESIGN".equals(node.criterion())));

        var aliceNode = graph.nodes().stream()
                .filter(node -> studentOneId.equals(node.studentId()))
                .findFirst()
                .orElseThrow();
        assertEquals("STUDENT", aliceNode.kind());
        assertEquals("LEADER", aliceNode.roleInTeam());
        assertEquals(0.75, aliceNode.sliceScore(), 0.0001);
        assertEquals(0.8, aliceNode.peerCoefficient(), 0.0001);
        assertEquals(0.6, aliceNode.adjustedScore(), 0.0001);
        assertEquals(70.5882, aliceNode.finalContributionPercentage(), 0.001);

        var aliceCodeEdge = graph.edges().stream()
                .filter(edge -> ("edge:CODE:" + studentOneId).equals(edge.id()))
                .findFirst()
                .orElseThrow();
        assertEquals("criterion:CODE", aliceCodeEdge.source());
        assertEquals("student:" + studentOneId, aliceCodeEdge.target());
        assertEquals(3.0, aliceCodeEdge.storyPoints(), 0.0001);
        assertEquals(0.75, aliceCodeEdge.weightedSlice(), 0.0001);
        assertEquals(1, aliceCodeEdge.tasks().size());
        assertEquals(taskOne.getId(), aliceCodeEdge.tasks().get(0).taskId());
        assertEquals("SAGA-1", aliceCodeEdge.tasks().get(0).externalKey());
    }

    @Test
    void weightsSprintsBySliceVolumeInsteadOfEqualAveragingPercentages() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sprintOneId = UUID.randomUUID();
        UUID sprintTwoId = UUID.randomUUID();
        UUID studentOneId = UUID.randomUUID();
        UUID studentTwoId = UUID.randomUUID();
        UUID aliceResearchId = UUID.randomUUID();
        UUID bobResearchId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setCodeContributionWeight(40.0);
        course.setTestContributionWeight(10.0);
        course.setDocumentContributionWeight(15.0);
        course.setResearchContributionWeight(35.0);

        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        Student studentOne = entityWithId(new Student(), studentOneId);
        studentOne.setFullName("Alice Nguyen");
        studentOne.setStudentCode("SE001");
        Student studentTwo = entityWithId(new Student(), studentTwoId);
        studentTwo.setFullName("Bob Tran");
        studentTwo.setStudentCode("SE002");
        TeamMember memberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        memberOne.setTeam(team);
        memberOne.setStudent(studentOne);
        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(studentTwo);

        Sprint sprintOne = entityWithId(new Sprint(), sprintOneId);
        sprintOne.setName("Sprint 1");
        Sprint sprintTwo = entityWithId(new Sprint(), sprintTwoId);
        sprintTwo.setName("Sprint 2");

        Task aliceCode = entityWithId(new Task(), UUID.randomUUID());
        aliceCode.setProject(project);
        aliceCode.setSprint(sprintOne);
        aliceCode.setAssignee(studentOne);
        aliceCode.setStatus(TaskStatus.DONE);
        aliceCode.setStoryPoint(10);
        aliceCode.setLabels(List.of("saga:code"));

        Task aliceResearch = entityWithId(new Task(), aliceResearchId);
        aliceResearch.setProject(project);
        aliceResearch.setSprint(sprintTwo);
        aliceResearch.setAssignee(studentOne);
        aliceResearch.setStatus(TaskStatus.DONE);
        aliceResearch.setStoryPoint(1);
        aliceResearch.setLabels(List.of("saga:research"));

        Task bobResearch = entityWithId(new Task(), bobResearchId);
        bobResearch.setProject(project);
        bobResearch.setSprint(sprintTwo);
        bobResearch.setAssignee(studentTwo);
        bobResearch.setStatus(TaskStatus.DONE);
        bobResearch.setStoryPoint(1);
        bobResearch.setLabels(List.of("saga:research"));

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(aliceCode, aliceResearch, bobResearch));
        when(taskAttachmentRepository.findByTask_Project_Id(projectId)).thenReturn(List.of(
                TaskAttachment.builder().task(aliceResearch).externalId("ar").build(),
                TaskAttachment.builder().task(bobResearch).externalId("br").build()
        ));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                List.of(studentOneId, studentTwoId), projectId
        )).thenReturn(List.of(
                peerReview(sprintOne, studentTwo, studentOne, 1),
                peerReview(sprintOne, studentOne, studentTwo, 1),
                peerReview(sprintTwo, studentTwo, studentOne, 1),
                peerReview(sprintTwo, studentOne, studentTwo, 1)
        ));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId))
                .thenReturn(List.of(sprintOne, sprintTwo));

        TeamContributionEvaluationResponse response = service.evaluate(teamId);
        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(studentOneId))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(studentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(100.0, alice.sprintBreakdowns().get(0).contributionPercentage(), 0.001);
        assertEquals(10.0, alice.sprintBreakdowns().get(0).codeStoryPoints(), 0.0001);
        assertEquals(0.0, alice.sprintBreakdowns().get(0).researchStoryPoints(), 0.0001);
        assertEquals(50.0, alice.sprintBreakdowns().get(1).contributionPercentage(), 0.001);
        assertEquals(1.0, alice.sprintBreakdowns().get(1).researchStoryPoints(), 0.0001);
        assertEquals(2, bob.sprintBreakdowns().size());
        assertEquals(0.0, bob.sprintBreakdowns().get(0).contributionPercentage(), 0.001);
        assertEquals(0.0, bob.sprintBreakdowns().get(0).codeStoryPoints(), 0.0001);
        assertEquals(50.0, bob.sprintBreakdowns().get(1).contributionPercentage(), 0.001);
        assertEquals(1.0, bob.sprintBreakdowns().get(1).researchStoryPoints(), 0.0001);
        // Σslice Alice = 10×0.40 + 1×0.35 = 4.35; Bob = 0.35; project P = 0.5 → 92.553 / 7.447.
        assertEquals(92.5532, alice.finalContributionPercentage(), 0.001);
        assertEquals(7.4468, bob.finalContributionPercentage(), 0.001);

        when(sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintOneId, projectId))
                .thenReturn(Optional.of(sprintOne));
        when(sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintTwoId, projectId))
                .thenReturn(Optional.of(sprintTwo));

        TeamContributionGraphResponse sprintOneGraph = service.graph(teamId, sprintOneId);
        assertEquals(sprintOneId, sprintOneGraph.sprintId());
        assertEquals("Sprint 1", sprintOneGraph.sprintName());
        assertEquals(1, sprintOneGraph.edges().size());
        var aliceSprintOne = sprintOneGraph.nodes().stream()
                .filter(node -> studentOneId.equals(node.studentId()))
                .findFirst()
                .orElseThrow();
        var bobSprintOne = sprintOneGraph.nodes().stream()
                .filter(node -> studentTwoId.equals(node.studentId()))
                .findFirst()
                .orElseThrow();
        assertEquals(4.0, aliceSprintOne.sliceScore(), 0.0001);
        assertEquals(0.5, aliceSprintOne.peerCoefficient(), 0.0001);
        assertEquals(2.0, aliceSprintOne.adjustedScore(), 0.0001);
        assertEquals(100.0, aliceSprintOne.finalContributionPercentage(), 0.001);
        assertEquals(0.0, bobSprintOne.sliceScore(), 0.0001);
        assertEquals(0.0, bobSprintOne.finalContributionPercentage(), 0.001);
        var sprintOneCodeEdge = sprintOneGraph.edges().get(0);
        assertEquals("edge:CODE:" + studentOneId, sprintOneCodeEdge.id());
        assertEquals(10.0, sprintOneCodeEdge.storyPoints(), 0.0001);
        assertEquals(4.0, sprintOneCodeEdge.weightedSlice(), 0.0001);

        TeamContributionGraphResponse sprintTwoGraph = service.graph(teamId, sprintTwoId);
        assertEquals(sprintTwoId, sprintTwoGraph.sprintId());
        assertEquals("Sprint 2", sprintTwoGraph.sprintName());
        assertEquals(2, sprintTwoGraph.edges().size());
        var aliceSprintTwo = sprintTwoGraph.nodes().stream()
                .filter(node -> studentOneId.equals(node.studentId()))
                .findFirst()
                .orElseThrow();
        var bobSprintTwo = sprintTwoGraph.nodes().stream()
                .filter(node -> studentTwoId.equals(node.studentId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0.35, aliceSprintTwo.sliceScore(), 0.0001);
        assertEquals(50.0, aliceSprintTwo.finalContributionPercentage(), 0.001);
        assertEquals(0.35, bobSprintTwo.sliceScore(), 0.0001);
        assertEquals(50.0, bobSprintTwo.finalContributionPercentage(), 0.001);
        assertTrue(sprintTwoGraph.edges().stream().allMatch(edge -> "RESEARCH".equals(edge.criterion())));
    }

    @Test
    void graphReturns404WhenSprintDoesNotBelongToProject() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID unknownSprintId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of());
        when(sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(unknownSprintId, projectId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.graph(teamId, unknownSprintId)
        );
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Sprint not found", exception.getReason());
    }

    @Test
    void doesNotRedistributeUnusedCourseSliceWeights() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentOneId = UUID.randomUUID();
        UUID studentTwoId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setCodeContributionWeight(60.0);
        course.setDocumentContributionWeight(20.0);
        course.setResearchContributionWeight(20.0);

        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);

        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        Student studentOne = entityWithId(new Student(), studentOneId);
        studentOne.setFullName("Alice Nguyen");
        studentOne.setStudentCode("SE001");

        Student studentTwo = entityWithId(new Student(), studentTwoId);
        studentTwo.setFullName("Bob Tran");
        studentTwo.setStudentCode("SE002");

        TeamMember memberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        memberOne.setTeam(team);
        memberOne.setStudent(studentOne);

        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(studentTwo);

        // Product decision: Task is the sole numeric Contribution authority. codeTask is both
        // a genuinely DONE+assigned Task (the real evidence channel) AND commit-linked (proving
        // the commit link contributes zero additional score — if it double-counted, Alice's
        // codeScore would be 4, not 2, and the 85.71/14.29 split below would not hold).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("saga:code"));
        codeTask.setAssignee(studentOne);
        codeTask.setStatus(com.saga.be.entity.enums.TaskStatus.DONE);
        Task documentTask = recognizedDocumentTask(studentTwo, 1);
        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");
        codeTask.setSprint(sprint);
        documentTask.setSprint(sprint);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(codeTask, documentTask));
        when(taskAttachmentRepository.findByTask_Project_Id(projectId)).thenReturn(List.of(attachmentOn(documentTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of(
                        peerReview(sprint, studentTwo, studentOne, 1),
                        peerReview(sprint, studentOne, studentTwo, 1)
                ));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));

        TeamContributionEvaluationResponse response = service.evaluate(teamId);

        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(studentOneId))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(studentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(85.7143, alice.finalContributionPercentage(), 0.001);
        assertEquals(14.2857, bob.finalContributionPercentage(), 0.001);
    }

    @Test
    void historicalProjectGroupWeightConfigIsNeverConsultedAndDoesNotChangeTheResult() {
        SliceWeightFixture fixture = sliceWeightFixture();

        // A historical row still exists in the DB (ProjectGroupWeightConfig is retained, not
        // dropped) but the resolver has no dependency on this repository anymore, so it is
        // structurally impossible for it to affect the result.
        org.mockito.Mockito.lenient().when(projectGroupWeightConfigRepository.findByProjectId(fixture.projectId()))
                .thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                        .project(fixture.project())
                        .team(fixture.team())
                        .codeWeight(new BigDecimal("0.2"))
                        .documentWeight(new BigDecimal("0.3"))
                        .designWeight(new BigDecimal("0.5"))
                        .build()));

        TeamContributionEvaluationResponse response = service.evaluate(fixture.teamId());

        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(fixture.studentOneId()))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(fixture.studentTwoId()))
                .findFirst()
                .orElseThrow();

        assertEquals(85.7143, alice.finalContributionPercentage(), 0.001);
        assertEquals(14.2857, bob.finalContributionPercentage(), 0.001);
    }

    @Test
    void secondTeamInTheSameCourseResolvesTheIdenticalCourseWeights() {
        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setCodeContributionWeight(60.0);
        course.setDocumentContributionWeight(20.0);
        course.setResearchContributionWeight(20.0);
        UUID otherTeamId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID otherStudentOneId = UUID.randomUUID();
        UUID otherStudentTwoId = UUID.randomUUID();

        Project otherProject = entityWithId(new Project(), otherProjectId);
        otherProject.setCourse(course);
        Team otherTeam = entityWithId(new Team(), otherTeamId);
        otherTeam.setCourse(course);
        otherTeam.setProject(otherProject);

        Student otherStudentOne = entityWithId(new Student(), otherStudentOneId);
        Student otherStudentTwo = entityWithId(new Student(), otherStudentTwoId);
        TeamMember otherMemberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        otherMemberOne.setTeam(otherTeam);
        otherMemberOne.setStudent(otherStudentOne);
        TeamMember otherMemberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        otherMemberTwo.setTeam(otherTeam);
        otherMemberTwo.setStudent(otherStudentTwo);

        // See doesNotRedistributeUnusedCourseSliceWeights for why codeTask is both a
        // genuinely DONE+assigned Task and commit-linked (proves the commit contributes zero
        // additional score under the Task-is-sole-authority rule).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("saga:code"));
        codeTask.setAssignee(otherStudentOne);
        codeTask.setStatus(TaskStatus.DONE);
        Task documentTask = recognizedDocumentTask(otherStudentTwo, 1);
        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");
        codeTask.setSprint(sprint);
        documentTask.setSprint(sprint);

        when(teamRepository.findWithCourseAndInstructorById(otherTeamId)).thenReturn(Optional.of(otherTeam));
        when(teamMemberRepository.findByTeamId(otherTeamId)).thenReturn(List.of(otherMemberOne, otherMemberTwo));
        when(taskRepository.findByProjectId(otherProjectId)).thenReturn(List.of(codeTask, documentTask));
        when(taskAttachmentRepository.findByTask_Project_Id(otherProjectId)).thenReturn(List.of(attachmentOn(documentTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(otherStudentOneId, otherProjectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(otherStudentTwoId, otherProjectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                List.of(otherStudentOneId, otherStudentTwoId), otherProjectId
        )).thenReturn(List.of(
                peerReview(sprint, otherStudentTwo, otherStudentOne, 1),
                peerReview(sprint, otherStudentOne, otherStudentTwo, 1)
        ));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(otherProjectId)).thenReturn(List.of(sprint));

        TeamContributionEvaluationResponse response = service.evaluate(otherTeamId);

        var studentOne = response.members().stream()
                .filter(member -> member.studentId().equals(otherStudentOneId))
                .findFirst()
                .orElseThrow();
        var studentTwo = response.members().stream()
                .filter(member -> member.studentId().equals(otherStudentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(85.7143, studentOne.finalContributionPercentage(), 0.001);
        assertEquals(14.2857, studentTwo.finalContributionPercentage(), 0.001);
    }

    private SliceWeightFixture sliceWeightFixture() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentOneId = UUID.randomUUID();
        UUID studentTwoId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setCodeContributionWeight(60.0);
        course.setDocumentContributionWeight(20.0);
        course.setResearchContributionWeight(20.0);

        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);

        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        Student studentOne = entityWithId(new Student(), studentOneId);
        studentOne.setFullName("Alice Nguyen");
        studentOne.setStudentCode("SE001");

        Student studentTwo = entityWithId(new Student(), studentTwoId);
        studentTwo.setFullName("Bob Tran");
        studentTwo.setStudentCode("SE002");

        TeamMember memberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        memberOne.setTeam(team);
        memberOne.setStudent(studentOne);

        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(studentTwo);

        // See doesNotRedistributeUnusedCourseSliceWeights for why codeTask is both a
        // genuinely DONE+assigned Task and commit-linked (proves the commit contributes zero
        // additional score under the Task-is-sole-authority rule).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("saga:code"));
        codeTask.setAssignee(studentOne);
        codeTask.setStatus(com.saga.be.entity.enums.TaskStatus.DONE);
        Task documentTask = recognizedDocumentTask(studentTwo, 1);
        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");
        codeTask.setSprint(sprint);
        documentTask.setSprint(sprint);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(codeTask, documentTask));
        when(taskAttachmentRepository.findByTask_Project_Id(projectId)).thenReturn(List.of(attachmentOn(documentTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of(
                        peerReview(sprint, studentTwo, studentOne, 1),
                        peerReview(sprint, studentOne, studentTwo, 1)
                ));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));

        return new SliceWeightFixture(teamId, projectId, studentOneId, studentTwoId, project, team);
    }

    private record SliceWeightFixture(
            UUID teamId,
            UUID projectId,
            UUID studentOneId,
            UUID studentTwoId,
            Project project,
            Team team
    ) {
    }

    @Test
    void lecturerOverrideAppliesImmediatelyWithoutAdminApproval() {
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setInstructor(lecturer);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);

        Student student = entityWithId(new Student(), studentId);
        TeamMember member = entityWithId(new TeamMember(), UUID.randomUUID());
        member.setTeam(team);
        member.setStudent(student);

        when(teamRepository.findById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(member));
        when(lecturerRepository.findById(lecturerId)).thenReturn(java.util.Optional.of(lecturer));
        when(policyOverrideRequestRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            PolicyOverrideRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(UUID.randomUUID());
            }
            return request;
        });

        var response = service.requestContributionOverride(
                principal(ApplicationRole.LECTURER, lecturerId),
                teamId,
                new com.saga.be.dto.request.ContributionOverrideRequest(studentId, 55.0, "Manual correction", lecturerId)
        );

        assertEquals(PolicyOverrideStatus.APPROVED, response.status());
        assertEquals("Contribution override applied successfully", response.message());
    }

    @Test
    void lecturerCannotOverrideAnotherCoursesTeam() {
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID ownerLecturerId = UUID.randomUUID();

        Lecturer ownerLecturer = entityWithId(new Lecturer(), ownerLecturerId);
        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setInstructor(ownerLecturer);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);

        when(teamRepository.findById(teamId)).thenReturn(java.util.Optional.of(team));

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.requestContributionOverride(
                        principal(ApplicationRole.LECTURER, UUID.randomUUID()),
                        teamId,
                        new com.saga.be.dto.request.ContributionOverrideRequest(studentId, 55.0, "Manual correction", ownerLecturerId)
                )
        );

        assertEquals("403 FORBIDDEN \"You can only override contributions for teams in your own courses\"", exception.getMessage());
    }

    @Test
    void taskWithExactSagaTestMarkerRoutesFullStoryPointToTest() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task testTask = new Task();
        testTask.setAssignee(studentFor(fixture));
        testTask.setSprint(fixture.sprint());
        testTask.setStatus(TaskStatus.DONE);
        testTask.setStoryPoint(5);
        testTask.setLabels(List.of("saga:test"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(testTask));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(100.0, member.testContributionScore(), 0.0001);
        assertEquals(100.0, member.testContributionPercentage(), 0.0001);
        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void commitsLinkedToAMarkedTaskNeverAddScoreRegardlessOfCount() {
        // Product decision test matrix cases B/C: Task is the sole numeric authority; 1 or 5
        // linked commits must never change the result versus zero linked commits.
        SingleStudentFixture fixture = singleStudentTeam();
        Task testTask = new Task();
        testTask.setAssignee(studentFor(fixture));
        testTask.setSprint(fixture.sprint());
        testTask.setStatus(TaskStatus.DONE);
        testTask.setStoryPoint(5);
        testTask.setLabels(List.of("saga:test"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(testTask));
        List<CommitData> fiveLinkedCommits = List.of(
                commitWithTask(testTask), commitWithTask(testTask), commitWithTask(testTask),
                commitWithTask(testTask), commitWithTask(testTask)
        );
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(fiveLinkedCommits);

        var member = evaluateSingle(fixture);

        assertEquals(100.0, member.testContributionScore(), 0.0001);
        assertEquals(100.0, member.testContributionPercentage(), 0.0001);
        // commitCountByStudent (fed into evidenceCount) still reflects the 5 linked commits for
        // evidence/warning purposes, even though none of them add numeric score.
        assertEquals(2, member.evidenceCount());
    }

    @Test
    void conflictingReservedMarkersExcludeTaskFromEveryCriterion() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task ambiguousTask = new Task();
        ambiguousTask.setAssignee(studentFor(fixture));
        ambiguousTask.setStatus(TaskStatus.DONE);
        ambiguousTask.setStoryPoint(5);
        ambiguousTask.setLabels(List.of("saga:test", "saga:research"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(ambiguousTask));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.testContributionScore(), 0.0001);
        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void ordinaryTaskWithoutReservedMarkerDoesNotEnterAnyCriterion() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task unlabeledDesignTask = new Task();
        unlabeledDesignTask.setAssignee(studentFor(fixture));
        unlabeledDesignTask.setStatus(TaskStatus.DONE);
        unlabeledDesignTask.setStoryPoint(5);
        unlabeledDesignTask.setLabels(List.of("ui-ux", "design"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(unlabeledDesignTask));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.testContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void commitsWithoutAnIndependentlyRegisteredTaskNeverMintScoreOnTheirOwn() {
        // Product decision test matrix case G (standalone commit): a commit's linked Task must
        // be a genuinely DONE+assigned Task (returned by taskRepository.findByProjectId) to be
        // scoring evidence — a commit alone, even with a non-null commit.getTask(), can never
        // manufacture score.
        SingleStudentFixture fixture = singleStudentTeam();
        Task neverRegisteredTask = new Task();
        neverRegisteredTask.setStoryPoint(5);
        neverRegisteredTask.setLabels(List.of("saga:code"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of(commitWithTask(neverRegisteredTask)));

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.testContributionScore(), 0.0001);
        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void documentAndResearchStoryPointsCountOnlyWithAttachmentAndDoNotGainExtraPoints() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentOneId = UUID.randomUUID();
        UUID studentTwoId = UUID.randomUUID();
        UUID aliceDocumentId = UUID.randomUUID();
        UUID aliceResearchId = UUID.randomUUID();
        UUID aliceCodeId = UUID.randomUUID();
        UUID bobDocumentId = UUID.randomUUID();
        UUID bobCodeId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);
        Student alice = entityWithId(new Student(), studentOneId);
        alice.setFullName("Alice Nguyen");
        alice.setStudentCode("SE001");
        Student bob = entityWithId(new Student(), studentTwoId);
        bob.setFullName("Bob Tran");
        bob.setStudentCode("SE002");
        TeamMember memberOne = entityWithId(new TeamMember(), UUID.randomUUID());
        memberOne.setTeam(team);
        memberOne.setStudent(alice);
        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(bob);
        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");

        Task aliceDocument = entityWithId(new Task(), aliceDocumentId);
        aliceDocument.setAssignee(alice);
        aliceDocument.setSprint(sprint);
        aliceDocument.setStatus(TaskStatus.DONE);
        aliceDocument.setStoryPoint(3);
        aliceDocument.setLabels(List.of("saga:document"));
        Task aliceResearch = entityWithId(new Task(), aliceResearchId);
        aliceResearch.setAssignee(alice);
        aliceResearch.setSprint(sprint);
        aliceResearch.setStatus(TaskStatus.DONE);
        aliceResearch.setStoryPoint(2);
        aliceResearch.setLabels(List.of("saga:research"));
        Task aliceCode = entityWithId(new Task(), aliceCodeId);
        aliceCode.setAssignee(alice);
        aliceCode.setSprint(sprint);
        aliceCode.setStatus(TaskStatus.DONE);
        aliceCode.setStoryPoint(4);
        aliceCode.setLabels(List.of("saga:code"));
        Task bobDocument = entityWithId(new Task(), bobDocumentId);
        bobDocument.setAssignee(bob);
        bobDocument.setSprint(sprint);
        bobDocument.setStatus(TaskStatus.DONE);
        bobDocument.setStoryPoint(3);
        bobDocument.setLabels(List.of("saga:document"));
        Task bobCode = entityWithId(new Task(), bobCodeId);
        bobCode.setAssignee(bob);
        bobCode.setSprint(sprint);
        bobCode.setStatus(TaskStatus.DONE);
        bobCode.setStoryPoint(5);
        bobCode.setLabels(List.of("saga:code"));
        Task bobResearch = entityWithId(new Task(), UUID.randomUUID());
        bobResearch.setAssignee(bob);
        bobResearch.setSprint(sprint);
        bobResearch.setStatus(TaskStatus.DONE);
        bobResearch.setStoryPoint(5);
        bobResearch.setLabels(List.of("saga:research"));

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId))
                .thenReturn(List.of(
                        aliceDocument, aliceResearch, aliceCode, bobDocument, bobCode, bobResearch
                ));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                List.of(studentOneId, studentTwoId), projectId
        )).thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));
        when(taskAttachmentRepository.findByTask_Project_Id(projectId)).thenReturn(List.of(
                TaskAttachment.builder().task(aliceDocument).externalId("a1").build(),
                TaskAttachment.builder().task(aliceDocument).externalId("a2").build(),
                TaskAttachment.builder().task(aliceResearch).externalId("a3").build(),
                TaskAttachment.builder().task(bobDocument).externalId("b1").build(),
                TaskAttachment.builder().task(aliceCode).externalId("a4").build()
        ));

        TeamContributionEvaluationResponse response = service.evaluate(teamId);
        var aliceResult = response.members().stream()
                .filter(member -> member.studentId().equals(studentOneId))
                .findFirst()
                .orElseThrow();
        var bobResult = response.members().stream()
                .filter(member -> member.studentId().equals(studentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(50.0, aliceResult.documentContributionScore(), 0.0001);
        assertEquals(50.0, bobResult.documentContributionScore(), 0.0001);
        assertEquals(100.0, aliceResult.researchContributionScore(), 0.0001);
        assertEquals(0.0, bobResult.researchContributionScore(), 0.0001);
        assertEquals(44.4444, aliceResult.codeContributionScore(), 0.001);
        assertEquals(55.5556, bobResult.codeContributionScore(), 0.001);
    }

    @Test
    void documentStoryPointsCountWhenTheTaskHasOnlyASubmittedLink() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task documentTask = recognizedDocumentTask(studentFor(fixture), 3);
        documentTask.setSprint(fixture.sprint());
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(documentTask));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());
        when(taskWebLinkRepository.findByTask_Project_Id(fixture.projectId())).thenReturn(List.of(
                TaskWebLink.builder()
                        .task(documentTask)
                        .url("https://www.figma.com/file/abc")
                        .build()
        ));

        var member = evaluateSingle(fixture);

        assertEquals(100.0, member.documentContributionScore(), 0.0001);
        assertEquals(100.0, member.finalContributionPercentage(), 0.0001);
    }

    @Test
    void unsprintedDoneTaskDoesNotContributeScore() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task unsprinted = new Task();
        unsprinted.setAssignee(studentFor(fixture));
        unsprinted.setStatus(TaskStatus.DONE);
        unsprinted.setStoryPoint(8);
        unsprinted.setLabels(List.of("saga:code"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(unsprinted));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(100.0, member.finalContributionPercentage(), 0.0001);
        assertTrue(member.sprintBreakdowns().isEmpty());
    }

    private record SingleStudentFixture(UUID teamId, UUID projectId, UUID studentId, Student student, Sprint sprint) {
    }

    private Student studentFor(SingleStudentFixture fixture) {
        return fixture.student();
    }

    private SingleStudentFixture singleStudentTeam() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);
        Student student = entityWithId(new Student(), studentId);
        student.setFullName("Solo Student");
        student.setStudentCode("SE100");
        TeamMember member = entityWithId(new TeamMember(), UUID.randomUUID());
        member.setTeam(team);
        member.setStudent(student);

        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(member));
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentId), projectId))
                .thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));
        when(taskAttachmentRepository.findByTask_Project_Id(projectId)).thenReturn(List.of());

        return new SingleStudentFixture(teamId, projectId, studentId, student, sprint);
    }

    private com.saga.be.dto.response.TeamContributionMemberResponse evaluateSingle(SingleStudentFixture fixture) {
        TeamContributionEvaluationResponse response = service.evaluate(fixture.teamId());
        return response.members().stream()
                .filter(item -> item.studentId().equals(fixture.studentId()))
                .findFirst()
                .orElseThrow();
    }

    private PeerReview peerReview(Sprint sprint, Student reviewer, Student reviewee, int stars) {
        PeerReview review = new PeerReview();
        review.setSprint(sprint);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setStarRating(stars);
        return review;
    }

    private Task recognizedDocumentTask(Student assignee, int storyPoint) {
        Task task = entityWithId(new Task(), UUID.randomUUID());
        task.setAssignee(assignee);
        task.setStatus(TaskStatus.DONE);
        task.setStoryPoint(storyPoint);
        task.setLabels(List.of("saga:document"));
        return task;
    }

    private TaskAttachment attachmentOn(Task task) {
        return TaskAttachment.builder().task(task).externalId("att-" + task.getId()).build();
    }

    private CommitData commitWithTask(Task task) {
        CommitData commit = new CommitData();
        commit.setTask(task);
        return commit;
    }

    private <T extends com.saga.be.entity.BaseEntity> T entityWithId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }

    private SagaPrincipal principal(ApplicationRole role, UUID localProfileId) {
        return new SagaPrincipal("sub", "user@example.com", "Test User", role, localProfileId, null);
    }
}
