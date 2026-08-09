package com.saga.be.dto.response;

import com.saga.be.service.AdminUserImportService.ImportRole;

/** Safe import summary: it intentionally contains no source-row identity data. */
public record AdminUserImportResponse(
        ImportRole role,
        int createdCount,
        int reusedCount
) {
}
