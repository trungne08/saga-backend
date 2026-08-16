package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.dto.response.CourseStatus;
import com.saga.be.entity.Semester;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CourseStatusResolverTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 31, 23, 59);

    @Test
    void resolvesOpenBetweenSemesterBoundaries() {
        assertEquals(CourseStatus.OPEN, resolveAt(LocalDateTime.of(2026, 3, 1, 12, 0), semester(START, END)));
    }

    @Test
    void resolvesOpenAtStartBoundary() {
        assertEquals(CourseStatus.OPEN, resolveAt(START, semester(START, END)));
    }

    @Test
    void resolvesOpenAtEndBoundary() {
        assertEquals(CourseStatus.OPEN, resolveAt(END, semester(START, END)));
    }

    @Test
    void resolvesClosedBeforeSemester() {
        assertEquals(CourseStatus.CLOSED, resolveAt(START.minusNanos(1), semester(START, END)));
    }

    @Test
    void resolvesClosedAfterSemester() {
        assertEquals(CourseStatus.CLOSED, resolveAt(END.plusNanos(1), semester(START, END)));
    }

    @Test
    void resolvesClosedWhenStartDateIsMissing() {
        assertEquals(CourseStatus.CLOSED, resolveAt(START, semester(null, END)));
    }

    @Test
    void resolvesClosedWhenEndDateIsMissing() {
        assertEquals(CourseStatus.CLOSED, resolveAt(START, semester(START, null)));
    }

    @Test
    void resolvesClosedWhenBothDatesAreMissing() {
        assertEquals(CourseStatus.CLOSED, resolveAt(START, semester(null, null)));
    }

    @Test
    void resolvesClosedWhenSemesterIsMissing() {
        assertEquals(CourseStatus.CLOSED, resolveAt(START, null));
    }

    private CourseStatus resolveAt(LocalDateTime localNow, Semester semester) {
        Clock clock = Clock.fixed(localNow.atZone(CourseStatusResolver.SEMESTER_ZONE).toInstant(), ZoneOffset.UTC);
        return new CourseStatusResolver(clock).resolve(semester);
    }

    private Semester semester(LocalDateTime startDate, LocalDateTime endDate) {
        return Semester.builder().startDate(startDate).endDate(endDate).build();
    }
}
