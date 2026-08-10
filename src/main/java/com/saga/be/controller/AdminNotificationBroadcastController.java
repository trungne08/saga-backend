package com.saga.be.controller;

import com.saga.be.dto.request.NotificationBroadcastRequest;
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
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Notifications", description = "Manual notification broadcasts for administrators.")
public class AdminNotificationBroadcastController {

    private final NotificationBroadcastService broadcastService;

    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast plain-text notification to a typed system audience")
    public ResponseEntity<NotificationBroadcastResponse> broadcast(
            @AuthenticationPrincipal SagaPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NotificationBroadcastRequest request
    ) {
        return ResponseEntity.ok(broadcastService.broadcastAdmin(
                principal,
                request,
                idempotencyKey
        ));
    }
}
