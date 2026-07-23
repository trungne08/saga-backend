package com.saga.be.controller;

import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.security.SagaPrincipal;
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
@RequestMapping("/api/auth")
public class AuthController {

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
        return AuthMeResponse.from(principal);
    }
}
