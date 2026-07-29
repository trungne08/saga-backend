package com.saga.be.integration.provider;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

public interface GitHubProviderClient {

    URI userAuthorizationUri(String state, String callbackUrl);

    String exchangeUserCode(String code, String callbackUrl);

    GitHubUserIdentity currentUser(String userAccessToken);

    URI installationUri(String state);

    GitHubInstallationInfo installation(long installationId);

    boolean userCanAccessInstallation(
            String userAccessToken,
            long installationId
    );

    List<GitHubRepositoryInfo> installationRepositories(long installationId);

    List<GitHubIssueSnapshot> issues(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    );

    List<GitHubCommitSnapshot> commits(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    );

    List<GitHubPullRequestSnapshot> pullRequests(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    );

    List<GitHubReviewSnapshot> reviews(
            long installationId,
            String owner,
            String repository,
            int pullNumber
    );

    List<GitHubCommentSnapshot> issueComments(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    );

    List<GitHubCommentSnapshot> reviewComments(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    );

    String installationAccessToken(long installationId);

    void invalidateInstallationToken(long installationId);
}
