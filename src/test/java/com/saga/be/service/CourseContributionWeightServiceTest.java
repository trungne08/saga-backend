package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.ContributionOverrideDecisionRequest;
import com.saga.be.dto.request.CourseContributionSliceWeightRequest;
import com.saga.be.dto.request.CourseContributionSliceWeightUpdateRequest;
import com.saga.be.dto.response.CourseContributionSliceWeightRequestResponse;
import com.saga.be.dto.response.CourseContributionSliceWeightResponse;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PolicyOverrideRequestRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
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
class CourseContributionWeightServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PolicyOverrideRequestRepository policyOverrideRequestRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    private CourseContributionWeightService service;

    @BeforeEach
    void setUp() {
        service = new CourseContributionWeightService(
                courseRepository,
                policyOverrideRequestRepository,
                lecturerRepository,
                adminRepository,
                teamRepository,
                projectGroupWeightConfigRepository
        );
    }

    @Test
    void requestWeightChangeCreatesPendingRequest() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        lecturer.setFullName("Dr. Tran");
        course.setInstructor(lecturer);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(lecturerRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(policyOverrideRequestRepository.save(any())).thenAnswer(invocation -> {
            PolicyOverrideRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(UUID.randomUUID());
            }
            return request;
        });

        CourseContributionSliceWeightRequestResponse response = service.requestWeightChange(
                courseId,
                new CourseContributionSliceWeightRequest(50.0, 30.0, 20.0, "Need more code emphasis", lecturerId)
        );

        assertEquals(courseId, response.courseId());
        assertEquals(PolicyOverrideStatus.PENDING, response.status());
        assertEquals(50.0, response.proposedCodeWeight(), 0.0001);
        assertEquals(30.0, response.proposedDocumentWeight(), 0.0001);
        assertEquals(20.0, response.proposedDesignWeight(), 0.0001);
    }

    @Test
    void approveWeightChangeUpdatesCourseWeights() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        lecturer.setFullName("Dr. Tran");
        course.setInstructor(lecturer);
        Admin admin = entityWithId(new Admin(), adminId);

        PolicyOverrideRequest request = entityWithId(new PolicyOverrideRequest(), requestId);
        request.setType("COURSE_SLICE_WEIGHT_OVERRIDE");
        request.setTargetConfigId(courseId);
        request.setLecturer(lecturer);
        request.setProposedCodeWeight(60.0f);
        request.setProposedDocumentWeight(25.0f);
        request.setProposedDesignWeight(15.0f);
        request.setStatus(PolicyOverrideStatus.PENDING);

        when(policyOverrideRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyOverrideRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseContributionSliceWeightRequestResponse response = service.decideWeightChangeRequest(
                requestId,
                new ContributionOverrideDecisionRequest("APPROVED", "Looks good", adminId)
        );

        assertEquals(PolicyOverrideStatus.APPROVED, response.status());
        assertEquals(60.0, course.getCodeContributionWeight(), 0.0001);
        assertEquals(25.0, course.getDocumentContributionWeight(), 0.0001);
        // Legacy flow proposes only Code/Document/Design (the retired triad); the legacy value
        // is preserved verbatim in the retained design_contribution_weight column, and testWeight
        // is forced to 0 (no legacy consumer proposes it).
        assertEquals(15.0, course.getDesignContributionWeight(), 0.0001);
        assertEquals(0.0, course.getTestContributionWeight(), 0.0001);
    }

    @Test
    void listWeightChangeRequestsSupportsStatusFiltering() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        lecturer.setFullName("Dr. Tran");

        PolicyOverrideRequest request = entityWithId(new PolicyOverrideRequest(), UUID.randomUUID());
        request.setType("COURSE_SLICE_WEIGHT_OVERRIDE");
        request.setTargetConfigId(courseId);
        request.setLecturer(lecturer);
        request.setProposedCodeWeight(55.0f);
        request.setProposedDocumentWeight(25.0f);
        request.setProposedDesignWeight(20.0f);
        request.setStatus(PolicyOverrideStatus.PENDING);

        when(policyOverrideRequestRepository.findByTypeAndStatusOrderByCreatedAtDesc(
                "COURSE_SLICE_WEIGHT_OVERRIDE",
                PolicyOverrideStatus.PENDING
        )).thenReturn(List.of(request));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        List<CourseContributionSliceWeightRequestResponse> responses = service.listWeightChangeRequests(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                PolicyOverrideStatus.PENDING,
                null
        );

        assertEquals(1, responses.size());
        assertEquals(courseId, responses.get(0).courseId());
        assertEquals(PolicyOverrideStatus.PENDING, responses.get(0).status());
    }

    @Test
    void lecturerCanListOwnWeightChangeRequestsWithoutCourseFilter() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        lecturer.setFullName("Dr. Tran");

        PolicyOverrideRequest request = entityWithId(new PolicyOverrideRequest(), UUID.randomUUID());
        request.setType("COURSE_SLICE_WEIGHT_OVERRIDE");
        request.setTargetConfigId(courseId);
        request.setLecturer(lecturer);
        request.setProposedCodeWeight(55.0f);
        request.setProposedDocumentWeight(25.0f);
        request.setProposedDesignWeight(20.0f);
        request.setStatus(PolicyOverrideStatus.PENDING);

        when(policyOverrideRequestRepository.findByTypeAndLecturer_IdOrderByCreatedAtDesc(
                "COURSE_SLICE_WEIGHT_OVERRIDE",
                lecturerId
        )).thenReturn(List.of(request));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        List<CourseContributionSliceWeightRequestResponse> responses = service.listWeightChangeRequests(
                principal(ApplicationRole.LECTURER, lecturerId),
                PolicyOverrideStatus.PENDING,
                null
        );

        assertEquals(1, responses.size());
        assertEquals(courseId, responses.get(0).courseId());
        assertEquals(PolicyOverrideStatus.PENDING, responses.get(0).status());
    }

    @Test
    void lecturerCannotListAnotherLecturersCourseRequests() {
        UUID courseId = UUID.randomUUID();
        UUID ownerLecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        Lecturer ownerLecturer = entityWithId(new Lecturer(), ownerLecturerId);
        course.setInstructor(ownerLecturer);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.listWeightChangeRequests(
                        principal(ApplicationRole.LECTURER, UUID.randomUUID()),
                        PolicyOverrideStatus.PENDING,
                        courseId
                )
        );

        assertEquals("403 FORBIDDEN \"You can only view override requests for your own courses\"", exception.getMessage());
    }

    @Test
    void requestWeightChangeRejectsInvalidWeightTotal() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requestWeightChange(
                        UUID.randomUUID(),
                        new CourseContributionSliceWeightRequest(40.0, 40.0, 10.0, "Invalid", UUID.randomUUID())
                )
        );

        assertEquals("400 BAD_REQUEST \"Slice weights must add up to 100\"", exception.getMessage());
    }

    @Test
    void lecturerOwnerCanUpdateCourseWeightsDirectlyWithoutAdminDecision() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        course.setInstructor(lecturer);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseContributionSliceWeightResponse response = service.updateCurrentWeights(
                principal(ApplicationRole.LECTURER, lecturerId),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(20.0, 10.0, 20.0, 50.0)
        );

        assertEquals(20.0, response.codeWeight(), 0.0001);
        assertEquals(10.0, response.testWeight(), 0.0001);
        assertEquals(20.0, response.documentWeight(), 0.0001);
        assertEquals(50.0, response.researchWeight(), 0.0001);
        assertEquals(20.0, course.getCodeContributionWeight(), 0.0001);
        assertEquals(10.0, course.getTestContributionWeight(), 0.0001);
        assertEquals(50.0, course.getResearchContributionWeight(), 0.0001);
    }

    @Test
    void lecturerCannotDirectlyUpdateAnotherCourseAndStudentIsForbidden() {
        UUID courseId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setInstructor(entityWithId(new Lecturer(), UUID.randomUUID()));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        assertThrows(ResponseStatusException.class, () -> service.updateCurrentWeights(
                principal(ApplicationRole.LECTURER, UUID.randomUUID()),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(20.0, 10.0, 20.0, 50.0)
        ));
        assertThrows(ResponseStatusException.class, () -> service.updateCurrentWeights(
                principal(ApplicationRole.STUDENT, UUID.randomUUID()),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(20.0, 10.0, 20.0, 50.0)
        ));
        assertThrows(ResponseStatusException.class, () -> service.updateCurrentWeights(
                principal(ApplicationRole.ADMIN, UUID.randomUUID()),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(20.0, 10.0, 20.0, 50.0)
        ));
    }

    @Test
    void lecturerCanReadOwnCourseWeightsButNotAnotherCourse() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setCourseCode("SE101");
        course.setName("Software Engineering");
        course.setCodeContributionWeight(30.0);
        course.setTestContributionWeight(10.0);
        course.setDocumentContributionWeight(20.0);
        course.setResearchContributionWeight(40.0);
        course.setInstructor(entityWithId(new Lecturer(), lecturerId));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        CourseContributionSliceWeightResponse response = service.getCurrentWeights(
                principal(ApplicationRole.LECTURER, lecturerId),
                courseId
        );
        assertEquals(30.0, response.codeWeight(), 0.0001);

        assertThrows(ResponseStatusException.class, () -> service.getCurrentWeights(
                principal(ApplicationRole.LECTURER, UUID.randomUUID()),
                courseId
        ));
    }

    @Test
    void directUpdateRejectsWhenFourWeightsDoNotSumToOneHundred() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setInstructor(entityWithId(new Lecturer(), lecturerId));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.updateCurrentWeights(
                principal(ApplicationRole.LECTURER, lecturerId),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(30.0, 30.0, 30.0, 30.0)
        ));

        assertEquals("400 BAD_REQUEST \"Slice weights must add up to 100\"", exception.getMessage());
    }

    @Test
    void directUpdateRejectsNegativeWeightAmongTheFourSlices() {
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Course course = entityWithId(new Course(), courseId);
        course.setInstructor(entityWithId(new Lecturer(), lecturerId));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.updateCurrentWeights(
                principal(ApplicationRole.LECTURER, lecturerId),
                courseId,
                new CourseContributionSliceWeightUpdateRequest(-10.0, 40.0, 40.0, 30.0)
        ));

        assertEquals("400 BAD_REQUEST \"Slice weights must be non-negative\"", exception.getMessage());
    }

    private <T extends com.saga.be.entity.BaseEntity> T entityWithId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }

    private SagaPrincipal principal(ApplicationRole role, UUID localProfileId) {
        return new SagaPrincipal("sub", "user@example.com", "Test User", role, localProfileId, null);
    }
}
