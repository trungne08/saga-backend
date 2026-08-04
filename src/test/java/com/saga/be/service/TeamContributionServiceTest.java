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
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
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
                lecturerRepository
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

        when(teamRepository.findById(teamId)).thenReturn(java.util.Optional.of(team));
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

        assertEquals(54.5454, alice.codeContributionScore(), 0.0001);
        assertEquals(54.5454, alice.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, alice.documentContributionPercentage(), 0.0001);
        assertEquals(0.0, alice.designContributionPercentage(), 0.0001);
        assertEquals(70.5882, alice.taskContributionScore(), 0.0001);
        assertEquals(0.8, alice.peerReviewScore(), 0.0001);
        assertEquals(60.0, alice.finalContributionPercentage(), 0.001);
        assertEquals(3, alice.evidenceCount());

        assertEquals(45.4545, bob.codeContributionScore(), 0.0001);
        assertEquals(45.4545, bob.codeContributionPercentage(), 0.0001);
        assertEquals(0.0, bob.documentContributionPercentage(), 0.0001);
        assertEquals(100.0, bob.designContributionPercentage(), 0.0001);
        assertEquals(29.4118, bob.taskContributionScore(), 0.0001);
        assertEquals(0.2, bob.peerReviewScore(), 0.0001);
        assertEquals(40.0, bob.finalContributionPercentage(), 0.001);
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
        course.setDesignContributionWeight(20.0);

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

        Task codeTask = new Task();
        codeTask.setStoryPoint(2);
        codeTask.setLabels(List.of("backend"));
        Document designDocument = new Document();
        designDocument.setAuthor(studentTwo);
        designDocument.setType(DocumentType.DESIGN);

        when(teamRepository.findById(teamId)).thenReturn(java.util.Optional.of(team));
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(memberOne, memberTwo));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(documentRepository.findByProjectId(projectId)).thenReturn(List.of(designDocument));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentOneId, projectId))
                .thenReturn(List.of(commitWithTask(codeTask)));
        when(commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentTwoId, projectId))
                .thenReturn(List.of());
        when(peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(List.of(studentOneId, studentTwoId), projectId))
                .thenReturn(List.of());
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of());

        TeamContributionEvaluationResponse response = service.evaluate(teamId);

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
