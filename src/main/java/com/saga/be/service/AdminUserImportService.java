package com.saga.be.service;

import com.saga.be.dto.response.AdminUserImportResponse;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
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
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Isolated global pre-provisioning flow. It never creates Course, Team, membership,
 * invitation, Cognito users, or modifies an existing profile.
 */
@Service
@RequiredArgsConstructor
public class AdminUserImportService {

    private static final List<String> STUDENT_HEADERS = List.of("studentCode", "email", "fullName");
    private static final List<String> LECTURER_HEADERS = List.of("email", "fullName");

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;
    private final StudentIdentityNormalizer identityNormalizer;

    @Transactional
    public AdminUserImportResponse importUsers(ImportRole role, MultipartFile file) {
        List<ImportRow> rows = parse(role, file);
        return switch (role) {
            case STUDENT -> importStudents(rows);
            case LECTURER -> importLecturers(rows);
        };
    }

    private AdminUserImportResponse importStudents(List<ImportRow> rows) {
        Set<String> emails = collectEmails(rows);
        Set<String> studentCodes = new HashSet<>();
        for (ImportRow row : rows) {
            studentCodes.add(row.studentCode());
        }

        Map<String, List<ProfileRef>> profilesByEmail = profilesByEmail(emails);
        Map<String, Student> studentsByCode = uniqueStudentsByCode(
                studentRepository.findAllByNormalizedStudentCodeIn(lowercase(studentCodes))
        );
        List<Student> toCreate = new ArrayList<>();
        int reused = 0;

        for (ImportRow row : rows) {
            List<ProfileRef> emailMatches = profilesByEmail.getOrDefault(row.email(), List.of());
            Student codeMatch = studentsByCode.get(identityNormalizer.normalizeEmail(row.studentCode()));
            if (emailMatches.isEmpty() && codeMatch == null) {
                toCreate.add(Student.builder()
                        .studentCode(row.studentCode())
                        .email(row.email())
                        .fullName(row.fullName())
                        .accountStatus(AccountStatus.PENDING)
                        .build());
                continue;
            }
            if (emailMatches.size() == 1
                    && emailMatches.get(0).role() == ApplicationRole.STUDENT
                    && codeMatch != null
                    && emailMatches.get(0).id().equals(codeMatch.getId())) {
                reused++;
                continue;
            }
            throw identityConflict();
        }
        studentRepository.saveAll(toCreate);
        return new AdminUserImportResponse(ImportRole.STUDENT, toCreate.size(), reused);
    }

    private AdminUserImportResponse importLecturers(List<ImportRow> rows) {
        Map<String, List<ProfileRef>> profilesByEmail = profilesByEmail(collectEmails(rows));
        List<Lecturer> toCreate = new ArrayList<>();
        int reused = 0;

        for (ImportRow row : rows) {
            List<ProfileRef> emailMatches = profilesByEmail.getOrDefault(row.email(), List.of());
            if (emailMatches.isEmpty()) {
                toCreate.add(Lecturer.builder()
                        .email(row.email())
                        .fullName(row.fullName())
                        .accountStatus(AccountStatus.ACTIVE)
                        .build());
                continue;
            }
            if (emailMatches.size() == 1 && emailMatches.get(0).role() == ApplicationRole.LECTURER) {
                reused++;
                continue;
            }
            throw identityConflict();
        }
        lecturerRepository.saveAll(toCreate);
        return new AdminUserImportResponse(ImportRole.LECTURER, toCreate.size(), reused);
    }

    private List<ImportRow> parse(ImportRole role, MultipartFile file) {
        if (file == null || file.isEmpty() || !hasXlsxFilename(file)) {
            throw invalidRequest();
        }
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() != 1) {
                throw invalidRequest();
            }
            Sheet sheet = workbook.getSheetAt(0);
            List<String> expectedHeaders = role == ImportRole.STUDENT ? STUDENT_HEADERS : LECTURER_HEADERS;
            validateHeader(sheet.getRow(0), expectedHeaders);
            List<ImportRow> rows = new ArrayList<>();
            Set<String> identityKeys = new HashSet<>();
            Set<String> emails = new HashSet<>();
            Set<String> studentCodes = new HashSet<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row)) {
                    continue;
                }
                ImportRow parsed = parseRow(role, row, expectedHeaders.size());
                if (!identityKeys.add(parsed.identityKey(role)) || !emails.add(parsed.email())
                        || (role == ImportRole.STUDENT && !studentCodes.add(parsed.studentCode()))) {
                    throw invalidRequest();
                }
                rows.add(parsed);
            }
            if (rows.isEmpty()) {
                throw invalidRequest();
            }
            return rows;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidRequest();
        }
    }

    private void validateHeader(Row header, List<String> expectedHeaders) {
        if (header == null || header.getLastCellNum() != expectedHeaders.size()) {
            throw invalidRequest();
        }
        for (int index = 0; index < expectedHeaders.size(); index++) {
            Cell cell = header.getCell(index);
            if (cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                    || !expectedHeaders.get(index).equals(readCell(cell))) {
                throw invalidRequest();
            }
        }
    }

    private ImportRow parseRow(ImportRole role, Row row, int expectedColumns) {
        if (row.getLastCellNum() > expectedColumns) {
            throw invalidRequest();
        }
        String studentCode = role == ImportRole.STUDENT ? requiredCell(row, 0, true, false) : null;
        int emailIndex = role == ImportRole.STUDENT ? 1 : 0;
        int fullNameIndex = role == ImportRole.STUDENT ? 2 : 1;
        String email = requiredCell(row, emailIndex, false, true);
        String fullName = requiredCell(row, fullNameIndex, false, false);
        return new ImportRow(studentCode, email, fullName);
    }

    private String requiredCell(Row row, int index, boolean studentCode, boolean email) {
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            throw invalidRequest();
        }
        String value = readCell(cell);
        if (studentCode) {
            value = identityNormalizer.normalizeStudentCode(value);
        } else if (email) {
            value = identityNormalizer.normalizeEmail(value);
        }
        if (value.isBlank()) {
            throw invalidRequest();
        }
        return value;
    }

    private String readCell(Cell cell) {
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = row.getFirstCellNum(); index >= 0 && index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index);
            if (cell != null && (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA || !readCell(cell).isBlank())) {
                return false;
            }
        }
        return true;
    }

    private Map<String, List<ProfileRef>> profilesByEmail(Collection<String> emails) {
        Map<String, List<ProfileRef>> result = new HashMap<>();
        for (Admin admin : adminRepository.findAllByNormalizedEmailIn(emails)) {
            addProfile(result, admin.getEmail(), new ProfileRef(ApplicationRole.ADMIN, admin.getId()));
        }
        for (Lecturer lecturer : lecturerRepository.findAllByNormalizedEmailIn(emails)) {
            addProfile(result, lecturer.getEmail(), new ProfileRef(ApplicationRole.LECTURER, lecturer.getId()));
        }
        for (Student student : studentRepository.findAllByNormalizedEmailIn(emails)) {
            addProfile(result, student.getEmail(), new ProfileRef(ApplicationRole.STUDENT, student.getId()));
        }
        return result;
    }

    private void addProfile(Map<String, List<ProfileRef>> profilesByEmail, String email, ProfileRef profile) {
        if (email != null && !email.isBlank()) {
            profilesByEmail.computeIfAbsent(identityNormalizer.normalizeEmail(email), unused -> new ArrayList<>())
                    .add(profile);
        }
    }

    private Map<String, Student> uniqueStudentsByCode(List<Student> students) {
        Map<String, Student> result = new HashMap<>();
        for (Student student : students) {
            String code = identityNormalizer.normalizeStudentCode(student.getStudentCode())
                    .toLowerCase(Locale.ROOT);
            if (result.put(code, student) != null) {
                throw identityConflict();
            }
        }
        return result;
    }

    private Set<String> collectEmails(List<ImportRow> rows) {
        Set<String> emails = new HashSet<>();
        for (ImportRow row : rows) {
            emails.add(row.email());
        }
        return emails;
    }

    private Set<String> lowercase(Set<String> values) {
        Set<String> lowercase = new HashSet<>();
        for (String value : values) {
            lowercase.add(value.toLowerCase(Locale.ROOT));
        }
        return lowercase;
    }

    private boolean hasXlsxFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_USER_IMPORT_FILE");
    }

    private IdentityConflictException identityConflict() {
        return new IdentityConflictException("The import identity conflicts with an existing local profile");
    }

    public enum ImportRole {
        STUDENT,
        LECTURER
    }

    private record ImportRow(String studentCode, String email, String fullName) {
        private String identityKey(ImportRole role) {
            return role == ImportRole.STUDENT ? studentCode + "\u0000" + email : email;
        }
    }

    private record ProfileRef(ApplicationRole role, java.util.UUID id) {
    }
}
