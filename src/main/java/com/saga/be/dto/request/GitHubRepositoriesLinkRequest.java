package com.saga.be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record GitHubRepositoriesLinkRequest(
        @Positive long installationId,
        @NotEmpty List<@Positive Long> repositoryIds
) {
}
