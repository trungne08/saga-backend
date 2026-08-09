package com.saga.be.controller;

import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.dto.response.CsrfTokenResponse;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CurrentAccountStatusService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Xác thực", description = "Đăng nhập, phiên làm việc và CSRF.")
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentAccountStatusService accountStatusService;

    public AuthController(CurrentAccountStatusService accountStatusService) {
        this.accountStatusService = accountStatusService;
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
        return AuthMeResponse.from(principal, accountStatusService.currentStatusForAuthRoute(principal));
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
}
