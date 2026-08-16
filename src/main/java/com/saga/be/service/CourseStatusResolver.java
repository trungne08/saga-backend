package com.saga.be.service;

import com.saga.be.dto.response.CourseStatus;
import com.saga.be.entity.Semester;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Resolves the Product-defined Course lifecycle from Semester business-calendar dates. */
@Component
public class CourseStatusResolver {

    static final ZoneId SEMESTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Clock clock;

    public CourseStatusResolver(Clock clock) {
        this.clock = clock;
    }

    public CourseStatus resolve(Semester semester) {
        if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
            return CourseStatus.CLOSED;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEMESTER_ZONE);
        return !now.isBefore(semester.getStartDate()) && !now.isAfter(semester.getEndDate())
                ? CourseStatus.OPEN
                : CourseStatus.CLOSED;
    }
}
