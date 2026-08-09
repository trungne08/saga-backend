package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Current explicit system default; all fields are null when no Semester is selected. */
public record ActiveSemesterSettingResponse(
        UUID semesterId,
        String semesterCode,
        String semesterName,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
}
