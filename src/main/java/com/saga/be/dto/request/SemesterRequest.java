package com.saga.be.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SemesterRequest {
    private String code;
    private String name;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}