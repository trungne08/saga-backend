package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.TeamMember;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Builds an XLSX snapshot from local, currently available Course data only. */
@Service
@RequiredArgsConstructor
public class AdminCourseReportExportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CourseRepository courseRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final PeerReviewRepository peerReviewRepository;

    @Transactional(readOnly = true)
    public ExportedCourseReport export(UUID courseId) {
        Course course = courseRepository.findWithReportDetailsByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        List<TeamMember> memberships = teamMemberRepository.findForCourseReportByCourseId(courseId);
        List<Sprint> sprints = sprintRepository
                .findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId);
        List<Task> tasks = taskRepository.findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId);
        List<PeerReview> reviews = peerReviewRepository
                .findBySprintBoardProjectCourseIdAndSprintDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId);
        return new ExportedCourseReport(filename(course), workbook(course, memberships, sprints, tasks, reviews));
    }

    private byte[] workbook(
            Course course,
            List<TeamMember> memberships,
            List<Sprint> sprints,
            List<Task> tasks,
            List<PeerReview> reviews
    ) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeCourse(workbook.createSheet("Course"), course);
            writeMembers(workbook.createSheet("Team Members"), memberships);
            writeSprints(workbook.createSheet("Sprints"), sprints);
            writeTasks(workbook.createSheet("Tasks"), tasks);
            writePeerReviews(workbook.createSheet("Peer Reviews"), reviews);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the Course report", exception);
        }
    }

    private void writeCourse(Sheet sheet, Course course) {
        addRow(sheet, "Course Code", "Course Name", "Semester Code", "Semester Name", "Subject Code",
                "Subject Name", "Class Code", "Class Name", "Lecturer Name");
        addRow(sheet, course.getCourseCode(), course.getName(),
                course.getSemester() == null ? null : course.getSemester().getCode(),
                course.getSemester() == null ? null : course.getSemester().getName(),
                course.getSubject() == null ? null : course.getSubject().getSubjectCode(),
                course.getSubject() == null ? null : course.getSubject().getName(),
                course.getClazz() == null ? null : course.getClazz().getClassCode(),
                course.getClazz() == null ? null : course.getClazz().getName(),
                course.getInstructor() == null ? null : course.getInstructor().getFullName());
    }

    private void writeMembers(Sheet sheet, List<TeamMember> memberships) {
        addRow(sheet, "Team", "Student Code", "Student Name", "Role", "Account Status");
        for (TeamMember membership : memberships) {
            addRow(sheet,
                    membership.getTeam() == null ? null : membership.getTeam().getName(),
                    membership.getStudent() == null ? null : membership.getStudent().getStudentCode(),
                    membership.getStudent() == null ? null : membership.getStudent().getFullName(),
                    membership.getRoleInTeam(),
                    membership.getStudent() == null ? null : membership.getStudent().getAccountStatus());
        }
    }

    private void writeSprints(Sheet sheet, List<Sprint> sprints) {
        addRow(sheet, "Sprint Name", "State", "Start Date", "End Date", "Completed Date");
        for (Sprint sprint : sprints) {
            addRow(sheet, sprint.getName(), sprint.getState(), sprint.getStartDate(), sprint.getEndDate(),
                    sprint.getCompleteDate());
        }
    }

    private void writeTasks(Sheet sheet, List<Task> tasks) {
        addRow(sheet, "Project", "Sprint", "Title", "Status", "Type", "Priority", "Story Point",
                "Assignee Student Code", "Due Date", "Resolved Date");
        for (Task task : tasks) {
            addRow(sheet,
                    task.getProject() == null ? null : task.getProject().getName(),
                    task.getSprint() == null ? null : task.getSprint().getName(),
                    task.getTitle(), task.getStatus(), task.getType(), task.getPriority(), task.getStoryPoint(),
                    task.getAssignee() == null ? null : task.getAssignee().getStudentCode(), task.getDueDate(),
                    task.getResolvedAt());
        }
    }

    private void writePeerReviews(Sheet sheet, List<PeerReview> reviews) {
        addRow(sheet, "Sprint", "Reviewer Student Code", "Reviewer Name", "Reviewee Student Code",
                "Reviewee Name", "Star Rating", "Created At");
        for (PeerReview review : reviews) {
            addRow(sheet,
                    review.getSprint() == null ? null : review.getSprint().getName(),
                    review.getReviewer() == null ? null : review.getReviewer().getStudentCode(),
                    review.getReviewer() == null ? null : review.getReviewer().getFullName(),
                    review.getReviewee() == null ? null : review.getReviewee().getStudentCode(),
                    review.getReviewee() == null ? null : review.getReviewee().getFullName(),
                    review.getStarRating(), review.getCreatedAt());
        }
    }

    private void addRow(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() == 0 && sheet.getPhysicalNumberOfRows() == 0 ? 0
                : sheet.getLastRowNum() + 1);
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            row.createCell(index).setCellValue(value instanceof LocalDateTime dateTime
                    ? DATE_TIME_FORMAT.format(dateTime)
                    : value == null ? "" : String.valueOf(value));
        }
    }

    private String filename(Course course) {
        String courseCode = course.getCourseCode() == null ? "course" : course.getCourseCode().trim();
        String safeCode = courseCode.replaceAll("[^A-Za-z0-9._-]", "_");
        return "course-report-" + (safeCode.isBlank() ? "course" : safeCode) + ".xlsx";
    }

    public record ExportedCourseReport(String filename, byte[] bytes) {
    }
}
