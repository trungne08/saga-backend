package com.saga.be.controller;

import com.saga.be.dto.response.NotificationResponse;
import com.saga.be.dto.response.NotificationUnreadCountResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
@Tag(name = "Thông báo", description = "Thông báo Bell, phát thông báo thủ công và đăng ký nhận thông báo đẩy.")
public class MyNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Xem danh sách thông báo của tôi")
    public ResponseEntity<Page<NotificationResponse>> list(
            @AuthenticationPrincipal SagaPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        return ResponseEntity.ok(notificationService.listMine(principal, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm số thông báo chưa đọc")
    public ResponseEntity<NotificationUnreadCountResponse> unreadCount(
            @AuthenticationPrincipal SagaPrincipal principal
    ) {
        return ResponseEntity.ok(notificationService.unreadCount(principal));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Đánh dấu thông báo là đã đọc")
    public ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID notificationId
    ) {
        return ResponseEntity.ok(notificationService.markRead(principal, notificationId));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100"
            );
        }
    }
}
