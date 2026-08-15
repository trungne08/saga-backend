package com.saga.be.service;

import com.saga.be.dto.request.CourseNotificationBroadcastRequest;
import com.saga.be.dto.request.NotificationBroadcastRequest;
import com.saga.be.dto.response.NotificationBroadcastResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.NotificationBroadcast;
import com.saga.be.entity.enums.NotificationBroadcastAudience;
import com.saga.be.entity.enums.NotificationBroadcastStatus;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private static final int FANOUT_BATCH_SIZE = 200;

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseRepository courseRepository;
    private final NotificationBroadcastFanoutService fanoutService;
    private final NotificationBroadcastPersistenceService persistenceService;

    public NotificationBroadcastResponse broadcastAdmin(
            SagaPrincipal principal,
            NotificationBroadcastRequest request,
            String idempotencyKey
    ) {
        requireRole(principal, ApplicationRole.ADMIN);
        NotificationBroadcast broadcast = claim(
                principal,
                request.audience(),
                request.title(),
                request.message(),
                idempotencyKey
        );
        if (broadcast.getStatus() != NotificationBroadcastStatus.COMPLETED) {
            switch (request.audience()) {
                case STUDENTS -> fanoutStudents(broadcast, NotificationType.MANUAL_ADMIN_BROADCAST);
                case LECTURERS -> fanoutLecturers(broadcast, NotificationType.MANUAL_ADMIN_BROADCAST);
                case ALL_USERS -> {
                    // Product confirms Student + Lecturer only. Admin inclusion remains TBD.
                    fanoutStudents(broadcast, NotificationType.MANUAL_ADMIN_BROADCAST);
                    fanoutLecturers(broadcast, NotificationType.MANUAL_ADMIN_BROADCAST);
                }
                case COURSE_STUDENTS -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "COURSE_STUDENTS is not an admin broadcast audience"
                );
            }
            broadcast = persistenceService.complete(broadcast.getId());
        }
        return NotificationBroadcastResponse.from(broadcast);
    }

    public NotificationBroadcastResponse broadcastLecturerCourses(
            SagaPrincipal principal,
            CourseNotificationBroadcastRequest request,
            String idempotencyKey
    ) {
        requireRole(principal, ApplicationRole.LECTURER);
        LinkedHashSet<UUID> courseIds = new LinkedHashSet<>(request.courseIds());
        if (courseIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseIds is required");
        }
        requireOwnedCourses(principal, courseIds);
        NotificationBroadcast broadcast = claim(
                principal,
                NotificationBroadcastAudience.COURSE_STUDENTS,
                request.title(),
                request.message(),
                idempotencyKey,
                String.join(",", courseIds.stream().map(UUID::toString).sorted().toList())
        );
        if (broadcast.getStatus() != NotificationBroadcastStatus.COMPLETED) {
            int page = 0;
            Page<UUID> recipients;
            do {
                recipients = teamMemberRepository.findDistinctStudentIdsByTeamCourseIdIn(
                        courseIds,
                        PageRequest.of(page++, FANOUT_BATCH_SIZE)
                );
                fanoutService.fanout(
                        broadcast.getId(),
                        recipients.getContent(),
                        ApplicationRole.STUDENT,
                        NotificationType.MANUAL_LECTURER_COURSE_BROADCAST,
                        broadcast.getTitle(),
                        broadcast.getMessage()
                );
            } while (recipients.hasNext());
            broadcast = persistenceService.complete(broadcast.getId());
        }
        return NotificationBroadcastResponse.from(broadcast);
    }

    private void fanoutStudents(NotificationBroadcast broadcast, NotificationType type) {
        fanoutAll(
                page -> studentRepository.findAllIds(page),
                broadcast,
                ApplicationRole.STUDENT,
                type
        );
    }

    private void fanoutLecturers(NotificationBroadcast broadcast, NotificationType type) {
        fanoutAll(
                page -> lecturerRepository.findAllIds(page),
                broadcast,
                ApplicationRole.LECTURER,
                type
        );
    }

    private void fanoutAll(
            java.util.function.Function<PageRequest, Page<UUID>> source,
            NotificationBroadcast broadcast,
            ApplicationRole recipientRole,
            NotificationType type
    ) {
        int page = 0;
        Page<UUID> recipients;
        do {
            recipients = source.apply(PageRequest.of(page++, FANOUT_BATCH_SIZE));
            fanoutService.fanout(
                    broadcast.getId(),
                    recipients.getContent(),
                    recipientRole,
                    type,
                    broadcast.getTitle(),
                    broadcast.getMessage()
            );
        } while (recipients.hasNext());
    }

    private NotificationBroadcast claim(
            SagaPrincipal principal,
            NotificationBroadcastAudience audience,
            String rawTitle,
            String rawMessage,
            String rawIdempotencyKey
    ) {
        return claim(principal, audience, rawTitle, rawMessage, rawIdempotencyKey, "");
    }

    private NotificationBroadcast claim(
            SagaPrincipal principal,
            NotificationBroadcastAudience audience,
            String rawTitle,
            String rawMessage,
            String rawIdempotencyKey,
            String scopeFingerprint
    ) {
        String title = requiredPlainText(rawTitle, 160, "title");
        String message = requiredPlainText(rawMessage, 1000, "message");
        String key = requiredIdempotencyKey(rawIdempotencyKey);
        String fingerprint = sha256(audience.name() + "\n" + scopeFingerprint + "\n" + title + "\n" + message);
        return persistenceService.claim(
                principal.localProfileId(),
                principal.applicationRole(),
                audience,
                title,
                message,
                key,
                fingerprint
        );
    }

    private void requireOwnedCourses(SagaPrincipal principal, LinkedHashSet<UUID> courseIds) {
        for (UUID courseId : courseIds) {
            Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
            if (course.getInstructor() == null
                    || !Objects.equals(course.getInstructor().getId(), principal.localProfileId())) {
                throw new AccessDeniedException("Only the assigned Lecturer may broadcast to this Course");
            }
        }
    }

    private void requireRole(SagaPrincipal principal, ApplicationRole role) {
        if (principal == null || principal.applicationRole() != role) {
            throw new AccessDeniedException("Notification broadcast is not permitted");
        }
    }

    private String requiredPlainText(String raw, int maxLength, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength || value.contains("<") || value.contains(">")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is invalid");
        }
        return value;
    }

    private String requiredIdempotencyKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.isEmpty() || key.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required and must be at most 128 characters"
            );
        }
        return key;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
