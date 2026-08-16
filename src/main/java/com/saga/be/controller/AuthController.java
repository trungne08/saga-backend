package com.saga.be.controller;

import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.dto.response.CsrfTokenResponse;
import com.saga.be.dto.request.SelfProfileUpdateRequest;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.security.AccountSessionEventHub;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CurrentAccountStatusService;
import com.saga.be.service.SelfProfileService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Xác thực", description = "Đăng nhập, phiên làm việc và CSRF.")
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentAccountStatusService accountStatusService;
    private final SelfProfileService selfProfileService;
    private final AccountSessionEventHub accountSessionEventHub;

    public AuthController(
            CurrentAccountStatusService accountStatusService,
            SelfProfileService selfProfileService,
            AccountSessionEventHub accountSessionEventHub
    ) {
        this.accountStatusService = accountStatusService;
        this.selfProfileService = selfProfileService;
        this.accountSessionEventHub = accountSessionEventHub;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/cognito"))
                .build();
    }

    @GetMapping("/me")
    public AuthMeResponse me(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpServletRequest request
    ) {
        if (principal == null) {
            throw new UnauthenticatedRequestException();
        }

        Object csrfAttribute = request.getAttribute(CsrfToken.class.getName());
        if (csrfAttribute instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        accountStatusService.currentStatusForAuthRoute(principal);
        return selfProfileService.read(principal);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Cap nhat ho so ca nhan",
            description = "Student va Lecturer chi cap nhat fullName/avatarUrl cua chinh minh qua session va CSRF; khong doi identity, vai tro hay trang thai."
    )
    public AuthMeResponse updateMe(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Valid @RequestBody SelfProfileUpdateRequest request
    ) {
        return selfProfileService.update(principal, request);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpServletRequest request
    ) {
        if (principal == null) {
            throw new UnauthenticatedRequestException();
        }

        Object csrfAttribute = request.getAttribute(CsrfToken.class.getName());
        if (csrfAttribute instanceof CsrfToken csrfToken) {
            return CsrfTokenResponse.from(csrfToken);
        }
        throw new IllegalStateException("Spring Security did not provide a CSRF token");
    }

    @GetMapping(value = "/session-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Nhận sự kiện phiên làm việc",
            description = "Luồng SSE theo browser session. Student/Lecturer nhận account-disabled khi tài khoản không còn ACTIVE. Không nhận Bearer, CSRF hay định danh từ client."
    )
    public SseEmitter sessionEvents(
            @AuthenticationPrincipal SagaPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (principal == null) {
            throw new UnauthenticatedRequestException();
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthenticatedRequestException();
        }
        response.setHeader("Cache-Control", "no-cache");
        try {
            return accountSessionEventHub.subscribe(principal, session);
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedRequestException();
        }
    }
}
