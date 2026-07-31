package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional
    public void importStudentsToCourse(UUID courseId, MultipartFile file) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // Bỏ qua dòng header (dòng 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 1. Đọc dữ liệu từng cột theo mẫu file
                String rollNumber = formatter.formatCellValue(row.getCell(1)).trim();
                String email = formatter.formatCellValue(row.getCell(2)).trim();
                String fullName = formatter.formatCellValue(row.getCell(4)).trim();
                String groupIndex = formatter.formatCellValue(row.getCell(5)).trim();
                String leaderMark = formatter.formatCellValue(row.getCell(6)).trim();

                if (rollNumber.isEmpty() || email.isEmpty()) continue;

                // 2. Tìm hoặc Tạo mới Student
                Student student = studentRepository.findByStudentCode(rollNumber)
                        .orElseGet(() -> {
                            Student newStudent = Student.builder()
                                    .studentCode(rollNumber)
                                    .email(email)
                                    .fullName(fullName)
                                    .accountStatus(AccountStatus.PENDING)
                                    .build();
                            return studentRepository.save(newStudent);
                        });

                // 3. Xử lý Team (Nhóm)
                if (!groupIndex.isEmpty()) {
                    String teamName = "Group " + groupIndex;
                    Team team = teamRepository.findByCourseIdAndName(course.getId(), teamName)
                            .orElseGet(() -> {
                                Team newTeam = Team.builder()
                                        .course(course)
                                        .name(teamName)
                                        .build();
                                return teamRepository.save(newTeam);
                            });

                    // 4. Thêm sinh viên vào Team (Sử dụng hàm Repository mới)
                    // Xác định Role trước
                    RoleInTeam role = leaderMark.equalsIgnoreCase("x") ? RoleInTeam.LEADER : RoleInTeam.MEMBER;
                    
                    // Kiểm tra sự tồn tại dựa trên cả teamId, studentId và role
                    boolean isMemberExist = teamMemberRepository.existsByTeamIdAndStudentIdAndRoleInTeam(
                            team.getId(), 
                            student.getId(), 
                            role
                    );

                    if (!isMemberExist) {
                        TeamMember teamMember = TeamMember.builder()
                                .team(team)
                                .student(student)
                                .roleInTeam(role)
                                .build();
                        teamMemberRepository.save(teamMember);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đọc file Excel: " + e.getMessage());
        }
    }
}