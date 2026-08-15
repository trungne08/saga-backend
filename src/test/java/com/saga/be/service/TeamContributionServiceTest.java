package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.Document;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Task;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.PolicyOverrideRequestRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
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
    private DocumentRepository documentRepository;

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

    private TeamContributionService service;

    @BeforeEach
    void setUp() {
        service = new TeamContributionService(
                teamRepository,
                teamMemberRepository,
                taskRepository,
                commitDataRepository,
                documentRepository,
                peerReviewRepository,
                sprintRepository,
                policyOverrideRequestRepository,
                lecturerRepository,
                new ContributionSliceWeightResolver(projectGroupWeightConfigRepository)
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

        TeamMember memberTwo = entityWithId(new TeamMember(), UUID.randomUUID());
        memberTwo.setTeam(team);
        memberTwo.setStudent(studentTwo);

        Sprint sprint = entityWithId(new Sprint(), sprintId);
        sprint.setName("Sprint 1");

        Task taskOne = entityWithId(new Task(), UUID.randomUUID());
        taskOne.setProject(project);
        taskOne.setSprint(sprint);
        taskOne.setAssignee(studentOne);
        taskOne.setStatus(TaskStatus.DONE);
        taskOne.setStoryPoint(3);

        Task taskTwo = entityWithId(new Task(), UUID.randomUUID());
        taskTwo.setProject(project);
        taskTwo.setSprint(sprint);
        taskTwo.setAssignee(studentTwo);
        taskTwo.setStatus(TaskStatus.DONE);
        taskTwo.setStoryPoint(5);

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
        when(documentRepository.findByProjectId(projectId)).thenReturn(List.of());
        Task linkedCodeTask = new Task();
        linkedCodeTask.setStoryPoint(1);
        linkedCodeTask.setLabels(List.of("backend"));
        Task linkedSecondCodeTask = new Task();
        linkedSecondCodeTask.setStoryPoint(1);
        linkedSecondCodeTask.setLabels(List.of("backend"));
        Task linkedDesignTask = new Task();
        linkedDesignTask.setStoryPoint(1);
        linkedDesignTask.setLabels(List.of("ui-ux", "design"));
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

        TeamContributionEvaluationResponse response = service.evaluate(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                teamId
        );

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
        // Only taskOne(storyPoint=3, CODE) and taskTwo(storyPoint=5, CODE — no reserved marker,
        // no design/document keyword, so it falls through the unchanged legacy classifier to
        // CODE) drive codeScore. Neither task has any DOCUMENT evidence, so totalDocument = 0.
        assertEquals(37.5, alice.codeContributionScore(), 0.0001);
        assertEquals(37.5, alice.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, alice.documentContributionPercentage(), 0.0001);
        assertEquals(70.5882, alice.taskContributionScore(), 0.0001);
        assertEquals(0.8, alice.peerReviewScore(), 0.0001);
        assertEquals(70.5882, alice.finalContributionPercentage(), 0.001);
        assertEquals(3, alice.evidenceCount());

        assertEquals(62.5, bob.codeContributionScore(), 0.0001);
        assertEquals(62.5, bob.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, bob.documentContributionPercentage(), 0.0001);
        assertEquals(29.4118, bob.taskContributionScore(), 0.0001);
        assertEquals(0.2, bob.peerReviewScore(), 0.0001);
        assertEquals(29.4118, bob.finalContributionPercentage(), 0.001);
    }

    @Test
    void normalizesCourseSliceWeightsAcrossActiveSlicesOnly() {
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
        // codeScore would be 4, not 2, and the 75/25 split below would not hold).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("backend"));
        codeTask.setAssignee(studentOne);
        codeTask.setStatus(com.saga.be.entity.enums.TaskStatus.DONE);
        Document designDocument = new Document();
        designDocument.setAuthor(studentTwo);
        designDocument.setType(DocumentType.DESIGN);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(codeTask));
        when(documentRepository.findByProjectId(projectId)).thenReturn(List.of(designDocument));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of());

        TeamContributionEvaluationResponse response = service.evaluate(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                teamId
        );

        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(studentOneId))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(studentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(75.0, alice.finalContributionPercentage(), 0.001);
        assertEquals(25.0, bob.finalContributionPercentage(), 0.001);
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

        TeamContributionEvaluationResponse response = service.evaluate(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                fixture.teamId()
        );

        var alice = response.members().stream()
                .filter(member -> member.studentId().equals(fixture.studentOneId()))
                .findFirst()
                .orElseThrow();
        var bob = response.members().stream()
                .filter(member -> member.studentId().equals(fixture.studentTwoId()))
                .findFirst()
                .orElseThrow();

        assertEquals(75.0, alice.finalContributionPercentage(), 0.001);
        assertEquals(25.0, bob.finalContributionPercentage(), 0.001);
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

        // See normalizesCourseSliceWeightsAcrossActiveSlicesOnly for why codeTask is both a
        // genuinely DONE+assigned Task and commit-linked (proves the commit contributes zero
        // additional score under the Task-is-sole-authority rule).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("backend"));
        codeTask.setAssignee(otherStudentOne);
        codeTask.setStatus(TaskStatus.DONE);
        Document designDocument = new Document();
        designDocument.setAuthor(otherStudentTwo);
        designDocument.setType(DocumentType.DESIGN);

        when(teamRepository.findWithCourseAndInstructorById(otherTeamId)).thenReturn(Optional.of(otherTeam));
        when(teamMemberRepository.findByTeamId(otherTeamId)).thenReturn(List.of(otherMemberOne, otherMemberTwo));
        when(taskRepository.findByProjectId(otherProjectId)).thenReturn(List.of(codeTask));
        when(documentRepository.findByProjectId(otherProjectId)).thenReturn(List.of(designDocument));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(otherStudentOneId, otherProjectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(otherStudentTwoId, otherProjectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                List.of(otherStudentOneId, otherStudentTwoId), otherProjectId
        )).thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(otherProjectId)).thenReturn(List.of());

        TeamContributionEvaluationResponse response = service.evaluate(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                otherTeamId
        );

        var studentOne = response.members().stream()
                .filter(member -> member.studentId().equals(otherStudentOneId))
                .findFirst()
                .orElseThrow();
        var studentTwo = response.members().stream()
                .filter(member -> member.studentId().equals(otherStudentTwoId))
                .findFirst()
                .orElseThrow();

        assertEquals(75.0, studentOne.finalContributionPercentage(), 0.001);
        assertEquals(25.0, studentTwo.finalContributionPercentage(), 0.001);
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

        // See normalizesCourseSliceWeightsAcrossActiveSlicesOnly for why codeTask is both a
        // genuinely DONE+assigned Task and commit-linked (proves the commit contributes zero
        // additional score under the Task-is-sole-authority rule).
        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("backend"));
        codeTask.setAssignee(studentOne);
        codeTask.setStatus(com.saga.be.entity.enums.TaskStatus.DONE);
        Document designDocument = new Document();
        designDocument.setAuthor(studentTwo);
        designDocument.setType(DocumentType.DESIGN);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(codeTask));
        when(documentRepository.findByProjectId(projectId)).thenReturn(List.of(designDocument));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of());

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
        testTask.setStatus(TaskStatus.DONE);
        testTask.setStoryPoint(5);
        testTask.setLabels(List.of("saga:test"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(testTask));
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
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
        testTask.setStatus(TaskStatus.DONE);
        testTask.setStoryPoint(5);
        testTask.setLabels(List.of("saga:test"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(testTask));
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
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
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.testContributionScore(), 0.0001);
        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void ordinaryTaskWithoutReservedMarkerStillUsesUnchangedLegacyKeywordClassifier() {
        SingleStudentFixture fixture = singleStudentTeam();
        Task legacyDesignTask = new Task();
        legacyDesignTask.setAssignee(studentFor(fixture));
        legacyDesignTask.setStatus(TaskStatus.DONE);
        legacyDesignTask.setStoryPoint(5);
        legacyDesignTask.setLabels(List.of("ui-ux", "design"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(legacyDesignTask));
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(100.0, member.documentContributionScore(), 0.0001);
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
        neverRegisteredTask.setLabels(List.of("backend"));
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of(commitWithTask(neverRegisteredTask)));

        var member = evaluateSingle(fixture);

        assertEquals(0.0, member.codeContributionScore(), 0.0001);
        assertEquals(0.0, member.testContributionScore(), 0.0001);
        assertEquals(0.0, member.documentContributionScore(), 0.0001);
        assertEquals(0.0, member.researchContributionScore(), 0.0001);
    }

    @Test
    void standaloneDocumentWithNoTaskRelationCountsExactlyAsBefore() {
        // Product decision test matrix case H: Document scoring has no Task dependency at all
        // and must be unaffected by this milestone's changes.
        SingleStudentFixture fixture = singleStudentTeam();
        Document standaloneDocument = new Document();
        standaloneDocument.setAuthor(studentFor(fixture));
        standaloneDocument.setType(DocumentType.REPORT);
        when(taskRepository.findByProjectId(fixture.projectId())).thenReturn(List.of());
        when(documentRepository.findByProjectId(fixture.projectId())).thenReturn(List.of(standaloneDocument));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(fixture.studentId(), fixture.projectId()))
                .thenReturn(List.of());

        var member = evaluateSingle(fixture);

        assertEquals(100.0, member.documentContributionScore(), 0.0001);
    }

    private record SingleStudentFixture(UUID teamId, UUID projectId, UUID studentId, Student student) {
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

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(member));
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentId), projectId))
                .thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of());

        return new SingleStudentFixture(teamId, projectId, studentId, student);
    }

    private com.saga.be.dto.response.TeamContributionMemberResponse evaluateSingle(SingleStudentFixture fixture) {
        TeamContributionEvaluationResponse response = service.evaluate(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                fixture.teamId()
        );
        return response.members().stream()
                .filter(item -> item.studentId().equals(fixture.studentId()))
                .findFirst()
                .orElseThrow();
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
