package com.saga.be.controller;

import com.saga.be.dto.request.CourseNotificationBroadcastRequest;
import com.saga.be.dto.response.NotificationBroadcastResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.NotificationBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LECTURER')")
@Tag(name = "Thông báo", description = "Thông báo Bell, phát thông báo thủ công và đăng ký nhận thông báo đẩy.")
public class CourseNotificationBroadcastController {

    private final NotificationBroadcastService broadcastService;

    @PostMapping("/broadcast")
    @Operation(summary = "Gửi thông báo đến sinh viên của các khóa học đang giảng dạy")
    public ResponseEntity<NotificationBroadcastResponse> broadcast(
            @AuthenticationPrincipal SagaPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CourseNotificationBroadcastRequest request
    ) {
        return ResponseEntity.ok(broadcastService.broadcastLecturerCourses(
                principal,
                request,
                idempotencyKey
        ));
    }
}
