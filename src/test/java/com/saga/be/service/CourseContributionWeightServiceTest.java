package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.CourseContributionSliceWeightUpdateRequest;
import com.saga.be.dto.response.CourseContributionSliceWeightResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
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
    private TeamRepository teamRepository;

    @Mock
    private ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    private CourseContributionWeightService service;

    @BeforeEach
    void setUp() {
        service = new CourseContributionWeightService(
                courseRepository,
                teamRepository,
                projectGroupWeightConfigRepository
        );
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
