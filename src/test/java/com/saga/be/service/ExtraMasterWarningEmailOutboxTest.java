package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Notification;
import com.saga.be.entity.Student;
import com.saga.be.entity.WarningEmailOutbox;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.WarningEmailStatus;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.WarningEmailOutboxRepository;
import com.saga.be.security.ApplicationRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ExtraMasterWarningEmailOutboxTest {

    @Test
    void enqueueOnceUsesProfileEmailAndDoesNotTouchInvitationLifecycle() {
        WarningEmailOutboxRepository outbox = mock(WarningEmailOutboxRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        WarningEmailOutboxService service = new WarningEmailOutboxService(outbox, students, events);
        UUID notificationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .recipientProfileId(studentId)
                .recipientRole(ApplicationRole.STUDENT)
                .notificationType(NotificationType.COMMIT_REVIEW_NEEDS_CHANGES)
                .title("Commit needs changes")
                .message("Safe summary")
                .eventKey("review:needs-changes:demo")
                .build();
        notification.setId(notificationId);
        Student student = Student.builder()
                .email("student@example.test")
                .fullName("Student")
                .studentCode("SE1")
                .build();
        student.setId(studentId);
        when(outbox.findByNotificationId(notificationId)).thenReturn(Optional.empty());
        when(students.findById(studentId)).thenReturn(Optional.of(student));
        when(outbox.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.enqueueOnce(notification);

        ArgumentCaptor<WarningEmailOutbox> captor = ArgumentCaptor.forClass(WarningEmailOutbox.class);
        verify(outbox).saveAndFlush(captor.capture());
        WarningEmailOutbox row = captor.getValue();
        assertEquals("student@example.test", row.getRecipientEmail());
        assertEquals(WarningEmailStatus.PENDING, row.getDeliveryStatus());
        assertEquals("Safe summary", row.getBodyText());
        assertFalse(row.getBodyHtml().contains("Bearer"));
        verify(events).publishEvent(any(WarningEmailQueued.class));
    }

    @Test
    void duplicateNotificationDoesNotCreateSecondOutboxRow() {
        WarningEmailOutboxRepository outbox = mock(WarningEmailOutboxRepository.class);
        WarningEmailOutboxService service = new WarningEmailOutboxService(
                outbox, mock(StudentRepository.class), mock(ApplicationEventPublisher.class)
        );
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .recipientProfileId(UUID.randomUUID())
                .recipientRole(ApplicationRole.STUDENT)
                .notificationType(NotificationType.UNLINKED_COMMIT_ADVISORY)
                .title("Advisory")
                .message("Unlinked")
                .eventKey("review:advisory:demo")
                .build();
        notification.setId(notificationId);
        when(outbox.findByNotificationId(notificationId))
                .thenReturn(Optional.of(WarningEmailOutbox.builder().build()));

        service.enqueueOnce(notification);

        verify(outbox, never()).saveAndFlush(any());
    }
}
