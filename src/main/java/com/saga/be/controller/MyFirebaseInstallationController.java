package com.saga.be.controller;

import com.saga.be.dto.request.FirebaseInstallationRegistrationRequest;
import com.saga.be.dto.response.FirebaseInstallationResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.FirebaseInstallationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/firebase-installations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
@Tag(name = "Thông báo", description = "Thông báo Bell, phát thông báo thủ công và đăng ký nhận thông báo đẩy.")
public class MyFirebaseInstallationController {

    private final FirebaseInstallationService installationService;

    @PostMapping
    @Operation(summary = "Đăng ký trình duyệt hiện tại để nhận thông báo đẩy")
    public ResponseEntity<FirebaseInstallationResponse> register(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Valid @RequestBody FirebaseInstallationRegistrationRequest request
    ) {
        return ResponseEntity.ok(installationService.register(
                principal,
                request.firebaseInstallationId()
        ));
    }

    @DeleteMapping("/{installationId}")
    @Operation(summary = "Ngừng nhận thông báo đẩy trên trình duyệt này")
    public ResponseEntity<FirebaseInstallationResponse> unregister(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID installationId
    ) {
        return ResponseEntity.ok(installationService.unregister(principal, installationId));
    }
}
