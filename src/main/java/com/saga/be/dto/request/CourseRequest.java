package com.saga.be.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class CourseRequest {
    private String courseCode;
    private String name;
    private UUID subjectId;
    private UUID classId;
    private UUID semesterId;
    private UUID instructorId;
}