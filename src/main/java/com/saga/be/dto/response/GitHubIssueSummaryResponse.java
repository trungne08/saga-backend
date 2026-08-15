package com.saga.be.dto.response;

import com.saga.be.entity.GitIssue;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IssueState;
import java.time.LocalDateTime;
import java.util.UUID;

public record GitHubIssueSummaryResponse(
        UUID id,
        Integer issueNumber,
        String title,
        IssueState state,
        RepositoryReference repository,
        StudentReference author,
        StudentReference assignee,
        LocalDateTime externalUpdatedAt,
        LocalDateTime closedAt
) {

    public static GitHubIssueSummaryResponse from(GitIssue issue) {
        return new GitHubIssueSummaryResponse(
                issue.getId(),
                issue.getIssueNumber(),
                issue.getTitle(),
                issue.getState(),
                new RepositoryReference(
                        issue.getRepo().getRepositoryId(),
                        issue.getRepo().getFullName()
                ),
                StudentReference.from(issue.getAuthor()),
                StudentReference.from(issue.getAssignee()),
                issue.getExternalUpdatedAt(),
                issue.getClosedAt()
        );
    }

    public record RepositoryReference(Long repositoryId, String fullName) {
    }

    public record StudentReference(UUID id, String fullName, String studentCode) {
        public static StudentReference from(Student student) {
            return student == null
                    ? null
                    : new StudentReference(
                            student.getId(),
                            student.getFullName(),
                            student.getStudentCode()
                    );
        }
    }
}
