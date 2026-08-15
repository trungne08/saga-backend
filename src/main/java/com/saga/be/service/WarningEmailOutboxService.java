package com.saga.be.service;

import com.saga.be.entity.Notification;
import com.saga.be.entity.Student;
import com.saga.be.entity.WarningEmailOutbox;
import com.saga.be.entity.enums.WarningEmailStatus;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.WarningEmailOutboxRepository;
import com.saga.be.security.ApplicationRole;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarningEmailOutboxService {

    private final WarningEmailOutboxRepository outbox;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;

    public WarningEmailOutboxService(
            WarningEmailOutboxRepository outbox,
            StudentRepository students,
            ApplicationEventPublisher events
    ) {
        this.outbox = outbox;
        this.students = students;
        this.events = events;
    }

    @Transactional
    public void enqueueOnce(Notification notification) {
        if (notification == null || notification.getId() == null) {
            return;
        }
        if (outbox.findByNotificationId(notification.getId()).isPresent()) {
            return;
        }
        if (notification.getRecipientRole() != ApplicationRole.STUDENT) {
            return;
        }
        Student student = students.findById(notification.getRecipientProfileId()).orElse(null);
        if (student == null || student.getEmail() == null || student.getEmail().isBlank()) {
            return;
        }
        WarningEmailOutbox row = outbox.saveAndFlush(WarningEmailOutbox.builder()
                .notification(notification)
                .recipientProfileId(notification.getRecipientProfileId())
                .recipientRole(notification.getRecipientRole())
                .recipientEmail(student.getEmail().trim())
                .subject(notification.getTitle())
                .bodyText(notification.getMessage())
                .bodyHtml("<p>" + escape(notification.getMessage()) + "</p>")
                .deliveryStatus(WarningEmailStatus.PENDING)
                .attemptCount(0)
                .build());
        events.publishEvent(new WarningEmailQueued(row.getId()));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
