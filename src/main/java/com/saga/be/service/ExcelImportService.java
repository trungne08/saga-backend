package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.CourseImportException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.SagaPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transactional import for the established Course spreadsheet schema. Parsing and
 * identity/membership preflight finish before this service creates Team/TeamMember/outbox rows.
 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final List<String> HEADERS = List.of(
            "Class", "RollNumber", "Email", "MemberCode", "FullName", "Group", "Leader"
    );
    private static final int MAX_ROWS = 1_000;
    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;

    private final CourseImportAuthorizationService authorizationService;
    private final StudentRepository studentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final StudentIdentityNormalizer identityNormalizer;
    private final StudentInvitationOutboxService invitationOutboxService;

    @Transactional
    public void importStudentsToCourse(SagaPrincipal principal, UUID courseId, MultipartFile file) {
        Course course = authorizationService.requireImportAccess(principal, courseId);
        List<ImportRow> rows = parse(file);
        ImportPlan plan = preflight(course, rows);
        persist(course, plan);
    }

    private List<ImportRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty() || !isXlsx(file)) {
            throw badRequest("MALFORMED_WORKBOOK", "The uploaded file must be a non-empty XLSX workbook");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw badRequest("FILE_TOO_LARGE", "The uploaded workbook exceeds the supported file size limit");
        }
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() < 1) {
                throw badRequest("MALFORMED_WORKBOOK", "The workbook must contain a sheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet.getRow(0));

            List<ImportRow> rows = new ArrayList<>();
            Set<String> studentCodes = new HashSet<>();
            Set<String> emails = new HashSet<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row)) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw badRequest("ROW_LIMIT", "The workbook exceeds the supported row limit");
                }
                ImportRow parsed = parseRow(row);
                if (!studentCodes.add(parsed.studentCode()) || !emails.add(parsed.email())) {
                    throw badRequest("DUPLICATE_IN_FILE", "The workbook contains duplicate student identity values");
                }
                rows.add(parsed);
            }
            if (rows.isEmpty()) {
                throw badRequest("INVALID_ROW", "The workbook contains no student rows");
            }
            return rows;
        } catch (CourseImportException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw badRequest("MALFORMED_WORKBOOK", "The uploaded file is not a readable XLSX workbook");
        }
    }

    private void validateHeader(Row header) {
        if (header == null || header.getLastCellNum() != HEADERS.size()) {
            throw badRequest("INVALID_HEADER", "The workbook header does not match the Course import schema");
        }
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = header.getCell(index);
            if (cell == null || cell.getCellType() == CellType.FORMULA) {
                throw badRequest("INVALID_HEADER", "The workbook header does not match the Course import schema");
            }
            if (!HEADERS.get(index).equals(readCell(cell))) {
                throw badRequest("INVALID_HEADER", "The workbook header does not match the Course import schema");
            }
        }
    }

    private ImportRow parseRow(Row row) {
        rejectFormulaCells(row);
        rejectUnexpectedValues(row);
        String studentCode = identityNormalizer.normalizeStudentCode(requiredCell(row, 1));
        String email = identityNormalizer.normalizeEmail(requiredCell(row, 2));
        String fullName = requiredCell(row, 4).trim();
        String groupIndex = readOptionalCell(row, 5);
        String leaderMark = readOptionalCell(row, 6);
        if (studentCode.isBlank() || email.isBlank() || fullName.isBlank()) {
            throw badRequest("INVALID_ROW", "A student row is missing a required value");
        }
        return new ImportRow(studentCode, email, fullName, groupIndex, leaderMark);
    }

    private ImportPlan preflight(Course course, List<ImportRow> rows) {
        Set<String> emails = new HashSet<>();
        Set<String> studentCodes = new HashSet<>();
        for (ImportRow row : rows) {
            emails.add(row.email());
            studentCodes.add(row.studentCode().toLowerCase(Locale.ROOT));
        }
        Map<String, Student> studentsByEmail = uniqueByEmail(
                studentRepository.findAllByNormalizedEmailIn(emails)
        );
        Map<String, Student> studentsByCode = uniqueByCode(
                studentRepository.findAllByNormalizedStudentCodeIn(studentCodes)
        );

        List<Student> newStudents = new ArrayList<>();
        Map<ImportRow, Student> existingStudents = new HashMap<>();
        for (ImportRow row : rows) {
            Student byEmail = studentsByEmail.get(row.email());
            Student byCode = studentsByCode.get(row.studentCode());
            if (byEmail == null && byCode == null) {
                newStudents.add(Student.builder()
                        .studentCode(row.studentCode()).email(row.email()).fullName(row.fullName())
                        .accountStatus(AccountStatus.PENDING).build());
            } else if (byEmail != null && byCode != null && byEmail.getId().equals(byCode.getId())) {
                existingStudents.put(row, byEmail);
            } else {
                throw conflict("IDENTITY_CONFLICT", "The student identity conflicts with an existing local profile");
            }
        }

        Set<String> requestedTeamNames = new HashSet<>();
        for (ImportRow row : rows) {
            if (!row.groupIndex().isBlank()) {
                requestedTeamNames.add(teamName(row.groupIndex()));
            }
        }
        Map<String, Team> teamsByName = new HashMap<>();
        if (!requestedTeamNames.isEmpty()) {
            for (Team team : teamRepository.findByCourseIdAndNameIn(course.getId(), requestedTeamNames)) {
                teamsByName.put(team.getName(), team);
            }
        }

        Set<UUID> existingStudentIds = new HashSet<>();
        for (Map.Entry<ImportRow, Student> entry : existingStudents.entrySet()) {
            if (!entry.getKey().groupIndex().isBlank()) {
                existingStudentIds.add(entry.getValue().getId());
            }
        }
        Map<UUID, List<TeamMember>> membershipsByStudent = membershipsByStudent(existingStudentIds, course.getId());
        for (Map.Entry<ImportRow, Student> entry : existingStudents.entrySet()) {
            ImportRow row = entry.getKey();
            if (row.groupIndex().isBlank()) {
                continue;
            }
            List<TeamMember> memberships = membershipsByStudent.getOrDefault(entry.getValue().getId(), List.of());
            String desiredTeamName = teamName(row.groupIndex());
            if (memberships.size() > 1 || (memberships.size() == 1
                    && !desiredTeamName.equals(memberships.get(0).getTeam().getName()))) {
                throw conflict("COURSE_TEAM_MEMBERSHIP_CONFLICT",
                        "The student already belongs to another Team in this Course");
            }
        }
        return new ImportPlan(rows, existingStudents, newStudents, teamsByName);
    }

    private void persist(Course course, ImportPlan plan) {
        List<Student> savedNewStudents = studentRepository.saveAll(plan.newStudents());
        Map<String, Student> studentsByIdentity = new HashMap<>();
        for (Student student : savedNewStudents) {
            studentsByIdentity.put(identityKey(student.getStudentCode(), student.getEmail()), student);
        }
        for (Map.Entry<ImportRow, Student> entry : plan.existingStudents().entrySet()) {
            studentsByIdentity.put(identityKey(entry.getKey().studentCode(), entry.getKey().email()), entry.getValue());
        }

        Set<UUID> membershipStudentIds = new HashSet<>();
        for (ImportRow row : plan.rows()) {
            if (!row.groupIndex().isBlank()) {
                membershipStudentIds.add(studentsByIdentity.get(identityKey(row.studentCode(), row.email())).getId());
            }
        }
        Map<UUID, Student> lockedStudents = lockedStudents(membershipStudentIds);
        Map<UUID, List<TeamMember>> membershipsByStudent = membershipsByStudent(membershipStudentIds, course.getId());
        Map<String, Team> teamsByName = new HashMap<>(plan.teamsByName());
        for (ImportRow row : plan.rows()) {
            if (row.groupIndex().isBlank()) {
                continue;
            }
            Student student = lockedStudents.get(studentsByIdentity.get(identityKey(row.studentCode(), row.email())).getId());
            String teamName = teamName(row.groupIndex());
            Team team = teamsByName.computeIfAbsent(teamName, unused -> teamRepository.save(
                    Team.builder().course(course).name(teamName).build()
            ));
            List<TeamMember> memberships = membershipsByStudent.getOrDefault(student.getId(), List.of());
            if (memberships.isEmpty()) {
                TeamMember membership = TeamMember.builder().team(team).student(student)
                        .roleInTeam(roleFor(row.leaderMark())).build();
                teamMemberRepository.save(membership);
                membershipsByStudent.put(student.getId(), List.of(membership));
            } else if (memberships.size() == 1 && memberships.get(0).getTeam().getId().equals(team.getId())) {
                // Same Student + Team is idempotent and intentionally preserves the existing role.
            } else {
                throw conflict("COURSE_TEAM_MEMBERSHIP_CONFLICT",
                        "The student already belongs to another Team in this Course");
            }
            invitationOutboxService.enqueueForCourse(student, course);
        }
    }

    private Map<UUID, Student> lockedStudents(Collection<UUID> ids) {
        Map<UUID, Student> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (Student student : teamMemberRepository.findStudentsForCourseImportWriteByIdIn(ids)) {
            result.put(student.getId(), student);
        }
        if (result.size() != ids.size()) {
            throw conflict("IDENTITY_CONFLICT", "An imported Student is no longer available");
        }
        return result;
    }

    private Map<UUID, List<TeamMember>> membershipsByStudent(Collection<UUID> studentIds, UUID courseId) {
        Map<UUID, List<TeamMember>> result = new HashMap<>();
        if (studentIds.isEmpty()) {
            return result;
        }
        for (TeamMember membership : teamMemberRepository.findByStudentIdInAndTeamCourseId(studentIds, courseId)) {
            result.computeIfAbsent(membership.getStudent().getId(), unused -> new ArrayList<>()).add(membership);
        }
        return result;
    }

    private Map<String, Student> uniqueByEmail(List<Student> students) {
        Map<String, Student> result = new HashMap<>();
        for (Student student : students) {
            if (result.put(identityNormalizer.normalizeEmail(student.getEmail()), student) != null) {
                throw conflict("IDENTITY_CONFLICT", "The student identity conflicts with an existing local profile");
            }
        }
        return result;
    }

    private Map<String, Student> uniqueByCode(List<Student> students) {
        Map<String, Student> result = new HashMap<>();
        for (Student student : students) {
            if (result.put(identityNormalizer.normalizeStudentCode(student.getStudentCode()), student) != null) {
                throw conflict("IDENTITY_CONFLICT", "The student identity conflicts with an existing local profile");
            }
        }
        return result;
    }

    private void rejectFormulaCells(Row row) {
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = row.getCell(index);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                throw badRequest("FORMULA_NOT_ALLOWED", "Formula cells are not allowed in Course import rows");
            }
        }
    }

    private void rejectUnexpectedValues(Row row) {
        for (int index = HEADERS.size(); index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                throw badRequest("FORMULA_NOT_ALLOWED", "Formula cells are not allowed in Course import rows");
            }
            if (cell != null && !readCell(cell).isBlank()) {
                throw badRequest("INVALID_ROW", "A student row contains unexpected values");
            }
        }
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = row.getFirstCellNum(); index >= 0 && index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index);
            if (cell != null && (cell.getCellType() == CellType.FORMULA || !readCell(cell).isBlank())) {
                return false;
            }
        }
        return true;
    }

    private String requiredCell(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : readCell(cell);
    }

    private String readOptionalCell(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : readCell(cell);
    }

    private String readCell(Cell cell) {
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }

    private boolean isXlsx(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private String teamName(String groupIndex) {
        return "Group " + groupIndex;
    }

    private RoleInTeam roleFor(String leaderMark) {
        return "x".equalsIgnoreCase(leaderMark) ? RoleInTeam.LEADER : RoleInTeam.MEMBER;
    }

    private String identityKey(String studentCode, String email) {
        return studentCode + "\u0000" + email;
    }

    private CourseImportException badRequest(String code, String message) {
        return new CourseImportException(HttpStatus.BAD_REQUEST, code, message);
    }

    private CourseImportException conflict(String code, String message) {
        return new CourseImportException(HttpStatus.CONFLICT, code, message);
    }

    private record ImportRow(String studentCode, String email, String fullName, String groupIndex, String leaderMark) {
    }

    private record ImportPlan(
            List<ImportRow> rows,
            Map<ImportRow, Student> existingStudents,
            List<Student> newStudents,
            Map<String, Team> teamsByName
    ) {
    }
}
