package com.saga.be.controller;

import com.saga.be.dto.request.IdentityMappingReviewRequest;
import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.integration.identity.IdentityMappingReviewService;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/identity-mappings")
public class IdentityMappingReviewController {

    private final IdentityMappingReviewService reviewService;

    public IdentityMappingReviewController(
            IdentityMappingReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<IdentityConnectionResponse> mappings(
            @AuthenticationPrincipal SagaPrincipal principal,
            @RequestParam UUID studentId
    ) {
        return reviewService.mappings(principal, studentId);
    }

    @PatchMapping("/{mappingId}")
    public IdentityConnectionResponse review(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID mappingId,
            @Valid @RequestBody IdentityMappingReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return reviewService.review(
                principal,
                mappingId,
                request,
                servletRequest.getRemoteAddr()
        );
    }
}
