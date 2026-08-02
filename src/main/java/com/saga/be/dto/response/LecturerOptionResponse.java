package com.saga.be.dto.response;

import com.saga.be.entity.Lecturer;
import java.util.UUID;

public record LecturerOptionResponse(
        UUID id,
        String fullName,
        String email
) {
    public static LecturerOptionResponse from(Lecturer lecturer) {
        return new LecturerOptionResponse(
                lecturer.getId(),
                lecturer.getFullName(),
                lecturer.getEmail()
        );
    }
}
