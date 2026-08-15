package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.PeerReviewCriterionRequest;
import com.saga.be.dto.response.PeerReviewCandidatesResponse;
import com.saga.be.dto.request.PeerReviewRequest;
import com.saga.be.dto.response.PeerReviewDefaultRubricResponse;
import com.saga.be.dto.response.PeerReviewResponse;
import com.saga.be.dto.response.PeerReviewRubricResponse;
import com.saga.be.dto.response.SprintPeerReviewResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Project;
import com.saga.be.entity.RubricTemplate;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.RubricTemplateRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PeerReviewServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private PeerReviewRepository peerReviewRepository;

    @Mock
    private RubricTemplateRepository rubricTemplateRepository;

    private PeerReviewService service;

    @BeforeEach
    void setUp() {
        service = new PeerReviewService(
                teamRepository,
                teamMemberRepository,
                sprintRepository,
                peerReviewRepository,
                rubricTemplateRepository
        );
    }

    @Test
    void submitStoresDetailedCriteriaAndTotalStars() {
        Fixture fixture = fixture();
        SagaPrincipal principal = studentPrincipal(fixture.reviewer.getId());
        PeerReviewRequest request = new PeerReviewRequest();
        request.setRevieweeId(fixture.reviewee.getId());
        request.setComment("Solid work");
        request.setCriteriaRatings(List.of(
                criterion(fixture.rubrics.get(0).getId(), 5),
                criterion(fixture.rubrics.get(1).getId(), 4),
                criterion(fixture.rubrics.get(2).getId(), 3),
                criterion(fixture.rubrics.get(3).getId(), 4)
        ));

        when(peerReviewRepository.findBySprintIdAndReviewerIdAndRevieweeId(
                fixture.sprint.getId(), fixture.reviewer.getId(), fixture.reviewee.getId()))
                .thenReturn(Optional.empty());
        when(sprintRepository.findByIdAndBoardProjectId(fixture.sprint.getId(), fixture.team.getProject().getId()))
                .thenReturn(Optional.of(fixture.sprint));
        when(rubricTemplateRepository.findBySubjectIdOrderByCreatedAtAsc(fixture.team.getCourse().getSubject().getId()))
                .thenReturn(fixture.rubrics);
        when(peerReviewRepository.saveAndFlush(any(PeerReview.class))).thenAnswer(invocation -> {
            PeerReview peerReview = invocation.getArgument(0);
            peerReview.setId(UUID.randomUUID());
            return peerReview;
        });

        PeerReviewResponse response = service.submit(
                principal,
                fixture.team.getId(),
                fixture.sprint.getId(),
                request
        );

        assertEquals(16, response.starRating());
        assertEquals(4, response.criteriaRatings().size());
        assertEquals("Communication", response.criteriaRatings().get(0).criteriaName());
        assertEquals(5, response.criteriaRatings().get(0).starRating());

        ArgumentCaptor<PeerReview> captor = ArgumentCaptor.forClass(PeerReview.class);
        org.mockito.Mockito.verify(peerReviewRepository).saveAndFlush(captor.capture());
        PeerReview saved = captor.getValue();
        assertEquals(16, saved.getStarRating());
        assertEquals(4, saved.getCriteriaRatings().size());
        assertEquals(0, saved.getCriteriaRatings().get(0).getCriteriaOrder());
        assertEquals("Communication", saved.getCriteriaRatings().get(0).getCriteriaName());
        assertNotNull(saved.getCriteriaRatings().get(0).getRubricTemplate());
    }

    @Test
    void getSprintReviewsReturnsDetailedCriteriaForLecturer() {
        Fixture fixture = fixture();
        SagaPrincipal lecturerPrincipal = new SagaPrincipal(
                "lecturer-sub",
                "lecturer@saga.test",
                "Lecturer",
                ApplicationRole.LECTURER,
                fixture.lecturerId,
                AccountStatus.ACTIVE
        );

        PeerReview peerReview = new PeerReview();
        peerReview.setId(UUID.randomUUID());
        peerReview.setSprint(fixture.sprint);
        peerReview.setReviewer(fixture.reviewer);
        peerReview.setReviewee(fixture.reviewee);
        peerReview.setStarRating(14);
        peerReview.setCriteriaRatings(List.of(
                detail(peerReview, fixture.rubrics.get(0), 0, 4),
                detail(peerReview, fixture.rubrics.get(1), 1, 3),
                detail(peerReview, fixture.rubrics.get(2), 2, 4),
                detail(peerReview, fixture.rubrics.get(3), 3, 3)
        ));

        when(peerReviewRepository.findBySprintIdAndRevieweeIdInAndReviewerIdInOrderByCreatedAtAsc(
                fixture.sprint.getId(),
                List.of(fixture.reviewer.getId(), fixture.reviewee.getId()),
                List.of(fixture.reviewer.getId(), fixture.reviewee.getId())))
                .thenReturn(List.of(peerReview));
        when(sprintRepository.findByIdAndBoardProjectId(fixture.sprint.getId(), fixture.team.getProject().getId()))
                .thenReturn(Optional.of(fixture.sprint));

        SprintPeerReviewResponse response = service.getSprintReviews(
                lecturerPrincipal,
                fixture.team.getId(),
                fixture.sprint.getId()
        );

        assertEquals(fixture.team.getId(), response.teamId());
        assertEquals(1, response.reviews().size());
        assertEquals(14, response.reviews().get(0).starRating());
        assertEquals(4, response.reviews().get(0).criteriaRatings().size());
        assertEquals("Quality", response.reviews().get(0).criteriaRatings().get(2).criteriaName());
    }

    @Test
    void getPeerReviewRubricReturnsSubjectCriteriaForStudentForm() {
        Fixture fixture = fixture();

        when(rubricTemplateRepository.findBySubjectIdOrderByCreatedAtAsc(fixture.team.getCourse().getSubject().getId()))
                .thenReturn(fixture.rubrics);

        PeerReviewRubricResponse response = service.getPeerReviewRubric(
                studentPrincipal(fixture.reviewer.getId()),
                fixture.team.getId()
        );

        assertEquals(fixture.team.getId(), response.teamId());
        assertEquals(fixture.team.getCourse().getSubject().getId(), response.subjectId());
        assertEquals(4, response.criteria().size());
        assertEquals("Communication", response.criteria().get(0).criteriaName());
    }

    @Test
    void getDefaultPeerReviewRubricReturnsGlobalCriteria() {
        Fixture fixture = fixture();

        when(rubricTemplateRepository.findBySubjectIdIsNullOrderByCreatedAtAsc())
                .thenReturn(fixture.rubrics);

        PeerReviewDefaultRubricResponse response = service.getDefaultPeerReviewRubric();

        assertEquals(4, response.criteria().size());
        assertEquals("Communication", response.criteria().get(0).criteriaName());
    }

    @Test
    void submitRejectsSelfReview() {
        Fixture fixture = fixture();
        SagaPrincipal principal = studentPrincipal(fixture.reviewer.getId());
        PeerReviewRequest request = new PeerReviewRequest();
        request.setRevieweeId(fixture.reviewer.getId());
        request.setStarRating(5);
        when(sprintRepository.findByIdAndBoardProjectId(fixture.sprint.getId(), fixture.team.getProject().getId()))
                .thenReturn(Optional.of(fixture.sprint));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(principal, fixture.team.getId(), fixture.sprint.getId(), request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("A student cannot peer review themselves", exception.getReason());
    }

    @Test
    void getReviewCandidatesExcludesReviewerAndShowsExistingReviewStatus() {
        Fixture fixture = fixture();
        Student thirdMember = entityWithId(new Student(), UUID.randomUUID());
        thirdMember.setFullName("Charlie");
        thirdMember.setStudentCode("SE003");
        TeamMember thirdTeamMember = entityWithId(new TeamMember(), UUID.randomUUID());
        thirdTeamMember.setStudent(thirdMember);
        thirdTeamMember.setTeam(fixture.team);
        when(teamMemberRepository.findByTeamId(fixture.team.getId()))
                .thenReturn(List.of(
                        memberFor(fixture.team, fixture.reviewer),
                        memberFor(fixture.team, fixture.reviewee),
                        thirdTeamMember
                ));
        when(sprintRepository.findByIdAndBoardProjectId(fixture.sprint.getId(), fixture.team.getProject().getId()))
                .thenReturn(Optional.of(fixture.sprint));

        PeerReview existingReview = new PeerReview();
        existingReview.setId(UUID.randomUUID());
        existingReview.setReviewer(fixture.reviewer);
        existingReview.setReviewee(fixture.reviewee);
        existingReview.setStarRating(15);
        when(peerReviewRepository.findBySprintIdAndReviewerIdAndRevieweeIdIn(
                eq(fixture.sprint.getId()),
                eq(fixture.reviewer.getId()),
                anyList()
        )).thenReturn(List.of(existingReview));

        PeerReviewCandidatesResponse response = service.getReviewCandidates(
                studentPrincipal(fixture.reviewer.getId()),
                fixture.team.getId(),
                fixture.sprint.getId()
        );

        assertEquals(fixture.team.getId(), response.teamId());
        assertEquals(fixture.sprint.getId(), response.sprintId());
        assertEquals(fixture.reviewer.getId(), response.reviewerId());
        assertEquals(2, response.candidates().size());
        Map<UUID, Boolean> reviewedFlags = response.candidates().stream()
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.studentId(),
                        candidate -> candidate.alreadyReviewed()
                ));
        Map<UUID, Integer> existingScores = response.candidates().stream()
                .filter(candidate -> candidate.existingTotalStarRating() != null)
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.studentId(),
                        candidate -> candidate.existingTotalStarRating()
                ));
        assertEquals(true, reviewedFlags.get(fixture.reviewee.getId()));
        assertEquals(false, reviewedFlags.get(thirdMember.getId()));
        assertEquals(15, existingScores.get(fixture.reviewee.getId()));
    }

    private Fixture fixture() {
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        Subject subject = entityWithId(new Subject(), subjectId);
        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setSubject(subject);
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        Project project = entityWithId(new Project(), projectId);
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);
        course.setInstructor(lecturer);

        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");

        Student reviewer = entityWithId(new Student(), UUID.randomUUID());
        reviewer.setFullName("Alice");
        Student reviewee = entityWithId(new Student(), UUID.randomUUID());
        reviewee.setFullName("Bob");

        TeamMember reviewerMember = entityWithId(new TeamMember(), UUID.randomUUID());
        reviewerMember.setStudent(reviewer);
        reviewerMember.setTeam(team);
        TeamMember revieweeMember = entityWithId(new TeamMember(), UUID.randomUUID());
        revieweeMember.setStudent(reviewee);
        revieweeMember.setTeam(team);

        RubricTemplate communication = rubric(subject, "Communication");
        RubricTemplate teamwork = rubric(subject, "Teamwork");
        RubricTemplate quality = rubric(subject, "Quality");
        RubricTemplate ownership = rubric(subject, "Ownership");
        List<RubricTemplate> rubrics = List.of(communication, teamwork, quality, ownership);

        lenient().when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        lenient().when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(reviewerMember, revieweeMember));

        return new Fixture(team, sprint, reviewer, reviewee, rubrics, lecturerId);
    }

    private SagaPrincipal studentPrincipal(UUID studentId) {
        return new SagaPrincipal(
                "student-sub",
                "student@saga.test",
                "Student",
                ApplicationRole.STUDENT,
                studentId,
                AccountStatus.ACTIVE
        );
    }

    private PeerReviewCriterionRequest criterion(UUID rubricId, int starRating) {
        PeerReviewCriterionRequest request = new PeerReviewCriterionRequest();
        request.setRubricId(rubricId);
        request.setStarRating(starRating);
        return request;
    }

    private com.saga.be.entity.PeerReviewDetail detail(
            PeerReview peerReview,
            RubricTemplate rubricTemplate,
            int order,
            int starRating
    ) {
        return com.saga.be.entity.PeerReviewDetail.builder()
                .peerReview(peerReview)
                .rubricTemplate(rubricTemplate)
                .criteriaName(rubricTemplate.getCriteriaName())
                .criteriaOrder(order)
                .starRating(starRating)
                .build();
    }

    private RubricTemplate rubric(Subject subject, String criteriaName) {
        RubricTemplate rubricTemplate = entityWithId(new RubricTemplate(), UUID.randomUUID());
        rubricTemplate.setSubject(subject);
        rubricTemplate.setCriteriaName(criteriaName);
        return rubricTemplate;
    }

    private TeamMember memberFor(Team team, Student student) {
        TeamMember teamMember = entityWithId(new TeamMember(), UUID.randomUUID());
        teamMember.setTeam(team);
        teamMember.setStudent(student);
        return teamMember;
    }

    private <T extends com.saga.be.entity.BaseEntity> T entityWithId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }

    private record Fixture(
            Team team,
            Sprint sprint,
            Student reviewer,
            Student reviewee,
            List<RubricTemplate> rubrics,
            UUID lecturerId
    ) {
    }
}
