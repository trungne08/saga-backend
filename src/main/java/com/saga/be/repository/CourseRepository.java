package com.saga.be.repository;

import com.saga.be.entity.Course;
import com.saga.be.dto.response.AdminCourseProgressOverviewRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    boolean existsByCourseCode(String courseCode);

    boolean existsByCourseCodeAndIdNot(String courseCode, UUID id);

    boolean existsBySubjectId(UUID subjectId);

    boolean existsByClazzId(UUID classId);

    boolean existsBySemesterId(UUID semesterId);

    Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Course> findByCourseCode(String courseCode);

    @Query(value = """
            SELECT c.id AS courseId,
                   c.course_code AS courseCode,
                   c.name AS courseName,
                   l.id AS lecturerId,
                   l.full_name AS lecturerFullName,
                   COUNT(DISTINCT t.id) AS teamCount,
                   COUNT(DISTINCT tm.student_id) AS studentCount,
                   COUNT(DISTINCT p.id) AS projectCount,
                   COUNT(DISTINCT s.id) AS sprintCount,
                   COUNT(DISTINCT CASE WHEN LOWER(s.state) = 'active' THEN s.id END) AS activeSprintCount,
                   COUNT(DISTINCT CASE WHEN LOWER(s.state) = 'closed' THEN s.id END) AS closedSprintCount,
                   COUNT(DISTINCT pr.id) AS peerReviewCount
            FROM course c
            LEFT JOIN lecturer l ON l.id = c.instructor_id
            LEFT JOIN team t ON t.course_id = c.id
            LEFT JOIN team_member tm ON tm.team_id = t.id
            LEFT JOIN project p ON p.course_id = c.id
            LEFT JOIN jira_board jb ON jb.project_id = p.id
            LEFT JOIN sprint s ON s.board_id = jb.id AND s.deleted_at IS NULL
            LEFT JOIN peer_review pr ON pr.sprint_id = s.id
            WHERE c.deleted_at IS NULL
              AND (:keyword IS NULL OR LOWER(c.course_code) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(c.name) LIKE CONCAT('%', LOWER(:keyword), '%'))
              AND (:semesterId IS NULL OR c.semester_id = :semesterId)
              AND (:lecturerId IS NULL OR c.instructor_id = :lecturerId)
            GROUP BY c.id, c.course_code, c.name, l.id, l.full_name
            ORDER BY c.course_code ASC, c.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM course c
            WHERE c.deleted_at IS NULL
              AND (:keyword IS NULL OR LOWER(c.course_code) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(c.name) LIKE CONCAT('%', LOWER(:keyword), '%'))
              AND (:semesterId IS NULL OR c.semester_id = :semesterId)
              AND (:lecturerId IS NULL OR c.instructor_id = :lecturerId)
            """, nativeQuery = true)
    Page<AdminCourseProgressOverviewRow> findAdminCourseProgressOverview(
            @Param("keyword") String keyword,
            @Param("semesterId") UUID semesterId,
            @Param("lecturerId") UUID lecturerId,
            Pageable pageable
    );
}
