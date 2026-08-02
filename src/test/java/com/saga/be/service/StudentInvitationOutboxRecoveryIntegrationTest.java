package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudentInvitationOutboxRecoveryIntegrationTest {

    @Autowired
    private StudentCourseInvitationRepository invitationRepository;
    @Autowired
    private StudentInvitationClaimService claimService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private CourseRepository courseRepository;

    @AfterEach
    void cleanUp() {
        invitationRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void concurrentWorkersDeliverOneInvitationOnly() throws Exception {
        StudentCourseInvitation invitation = invitation(StudentInvitationStatus.PENDING, 0, null);
        AtomicInteger deliveries = new AtomicInteger();
        StudentInvitationProcessor processor = processor(message -> deliveries.incrementAndGet());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> processAfterBarrier(processor, invitation.getId(), ready, start));
            Future<?> second = executor.submit(() -> processAfterBarrier(processor, invitation.getId(), ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();

            assertEquals(1, deliveries.get());
            assertEquals(StudentInvitationStatus.SENT, invitation(invitation.getId()).getInvitationStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sentInvitationDoesNotCallProviderAgain() {
        StudentCourseInvitation invitation = invitation(StudentInvitationStatus.SENT, 1, null);
        AtomicInteger deliveries = new AtomicInteger();

        processor(message -> deliveries.incrementAndGet()).process(invitation.getId());

        assertEquals(0, deliveries.get());
    }

    @Test
    void exhaustedInvitationDoesNotCallProviderAgain() {
        StudentCourseInvitation invitation = invitation(StudentInvitationStatus.FAILED, 5, null);
        AtomicInteger deliveries = new AtomicInteger();

        processor(message -> deliveries.incrementAndGet()).process(invitation.getId());

        assertEquals(0, deliveries.get());
        assertEquals(StudentInvitationStatus.FAILED, invitation(invitation.getId()).getInvitationStatus());
    }

    @Test
    void freshProcessingInvitationIsNotReclaimed() {
        StudentCourseInvitation invitation = invitation(
                StudentInvitationStatus.PROCESSING,
                1,
                LocalDateTime.now()
        );

        boolean recovered = claimService.recoverStaleProcessing(
                invitation.getId(),
                LocalDateTime.now().minusMinutes(1)
        );

        assertFalse(recovered);
        assertEquals(StudentInvitationStatus.PROCESSING, invitation(invitation.getId()).getInvitationStatus());
    }

    @Test
    void staleProcessingInvitationIsRecoveredAndRetried() {
        StudentCourseInvitation invitation = invitation(
                StudentInvitationStatus.PROCESSING,
                1,
                LocalDateTime.now().minusMinutes(10)
        );
        AtomicInteger deliveries = new AtomicInteger();

        boolean recovered = claimService.recoverStaleProcessing(
                invitation.getId(),
                LocalDateTime.now().minusMinutes(1)
        );
        processor(message -> deliveries.incrementAndGet()).process(invitation.getId());

        assertTrue(recovered);
        assertEquals(1, deliveries.get());
        assertEquals(StudentInvitationStatus.SENT, invitation(invitation.getId()).getInvitationStatus());
        assertEquals(2, invitation(invitation.getId()).getAttemptCount());
    }

    private void processAfterBarrier(
            StudentInvitationProcessor processor,
            UUID invitationId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await();
            processor.process(invitationId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private StudentInvitationProcessor processor(StudentInvitationDeliveryAdapter adapter) {
        StudentInvitationProperties properties = new StudentInvitationProperties();
        properties.setProcessingTimeoutMs(1000);
        return new StudentInvitationProcessor(
                invitationRepository,
                claimService,
                adapter,
                properties
        );
    }

    private StudentCourseInvitation invitation(
            StudentInvitationStatus status,
            int attempts,
            LocalDateTime processingStartedAt
    ) {
        String suffix = UUID.randomUUID().toString();
        Student student = studentRepository.save(Student.builder()
                .email("student-" + suffix + "@example.test")
                .studentCode("SE" + suffix.substring(0, 6).toUpperCase())
                .accountStatus(AccountStatus.PENDING)
                .build());
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-" + suffix)
                .name("Invitation course")
                .build());
        return invitationRepository.save(StudentCourseInvitation.builder()
                .student(student)
                .course(course)
                .invitationType(StudentInvitationType.FIRST_LOGIN_REQUIRED)
                .invitationStatus(status)
                .attemptCount(attempts)
                .processingStartedAt(processingStartedAt)
                .build());
    }

    private StudentCourseInvitation invitation(UUID invitationId) {
        return invitationRepository.findById(invitationId).orElseThrow();
    }
}
