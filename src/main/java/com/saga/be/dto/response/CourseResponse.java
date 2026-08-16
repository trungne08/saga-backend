package com.saga.be.dto.response;

import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Subject;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.ContributionConfigMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stable public representation for Course read endpoints.
 *
 * <p>The persistence association remains {@code Course.clazz} / {@code course.class_id};
 * {@code academicClass} is the sole public JSON field for that association.</p>
 */
public record CourseResponse(
        UUID id,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String courseCode,
        String name,
        SubjectResponse subject,
        AcademicClassResponse academicClass,
        SemesterResponse semester,
        CourseStatus courseStatus,
        InstructorResponse instructor,
        Double codeContributionWeight,
        Double testContributionWeight,
        Double documentContributionWeight,
        Double researchContributionWeight,
        ContributionConfigMode contributionConfigMode
) {
    public static CourseResponse from(Course course, CourseStatus courseStatus) {
        AcademicClassResponse classResponse = AcademicClassResponse.from(course.getClazz());
        return new CourseResponse(
                course.getId(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getCourseCode(),
                course.getName(),
                SubjectResponse.from(course.getSubject()),
                classResponse,
                SemesterResponse.from(course.getSemester()),
                courseStatus,
                InstructorResponse.from(course.getInstructor()),
                course.getCodeContributionWeight(),
                course.getTestContributionWeight(),
                course.getDocumentContributionWeight(),
                course.getResearchContributionWeight(),
                course.getContributionConfigMode()
        );
    }

    public record SubjectResponse(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String subjectCode,
            String name
    ) {
        private static SubjectResponse from(Subject subject) {
            return subject == null ? null : new SubjectResponse(
                    subject.getId(), subject.getCreatedAt(), subject.getUpdatedAt(),
                    subject.getSubjectCode(), subject.getName()
            );
        }
    }

    public record AcademicClassResponse(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String classCode,
            String name
    ) {
        private static AcademicClassResponse from(com.saga.be.entity.Class clazz) {
            return clazz == null ? null : new AcademicClassResponse(
                    clazz.getId(), clazz.getCreatedAt(), clazz.getUpdatedAt(),
                    clazz.getClassCode(), clazz.getName()
            );
        }
    }

    public record SemesterResponse(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String code,
            String name,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        private static SemesterResponse from(Semester semester) {
            return semester == null ? null : new SemesterResponse(
                    semester.getId(), semester.getCreatedAt(), semester.getUpdatedAt(),
                    semester.getCode(), semester.getName(), semester.getStartDate(), semester.getEndDate()
            );
        }
    }

    public record InstructorResponse(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String cognitoSub,
            String email,
            String fullName,
            String avatarUrl,
            AccountStatus accountStatus
    ) {
        private static InstructorResponse from(Lecturer instructor) {
            return instructor == null ? null : new InstructorResponse(
                    instructor.getId(), instructor.getCreatedAt(), instructor.getUpdatedAt(),
                    instructor.getCognitoSub(), instructor.getEmail(), instructor.getFullName(),
                    instructor.getAvatarUrl(), instructor.getAccountStatus()
            );
        }
    }
}
