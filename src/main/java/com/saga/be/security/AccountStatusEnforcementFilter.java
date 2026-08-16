package com.saga.be.security;

import com.saga.be.service.CurrentAccountStatusService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the current local status for authenticated browser sessions. */
@Component
public class AccountStatusEnforcementFilter extends OncePerRequestFilter {

    private final CurrentAccountStatusService accountStatusService;
    private final SecurityErrorResponseWriter responseWriter;

    public AccountStatusEnforcementFilter(
            CurrentAccountStatusService accountStatusService,
            SecurityErrorResponseWriter responseWriter
    ) {
        this.accountStatusService = accountStatusService;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/auth/csrf")
                || path.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session == null || !(authentication != null && authentication.getPrincipal() instanceof SagaPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!accountStatusService.isAllowedForBusinessApi(principal)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            responseWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "ACCOUNT_DISABLED",
                    "Tài khoản của bạn đã bị vô hiệu hóa."
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
