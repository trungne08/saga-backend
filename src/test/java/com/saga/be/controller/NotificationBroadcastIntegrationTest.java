package com.saga.be.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.entity.Course;
import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.FirebaseInstallationRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.NotificationBroadcastRepository;
import com.saga.be.repository.NotificationDeliveryRepository;
import com.saga.be.repository.NotificationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notificationbroadcast;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CLASS,COMMENT")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationBroadcastIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationBroadcastRepository broadcastRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private FirebaseInstallationRepository installationRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LecturerRepository lecturerRepository;

    @BeforeEach
    void clean() {
        deliveryRepository.deleteAll();
        installationRepository.deleteAll();
        notificationRepository.deleteAll();
        broadcastRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void adminStudentsBroadcastIsIdempotentAndCreatesOneBellRowPerRecipient() throws Exception {
        Student first = student("S1");
        Student second = student("S2");
        lecturer("L1");
        installationRepository.save(FirebaseInstallation.builder()
                .ownerProfileId(first.getId()).ownerRole(ApplicationRole.STUDENT)
                .firebaseInstallationId("fid-admin-broadcast")
                .active(true).lastRegisteredAt(LocalDateTime.now()).build());

        String request = "{\"audience\":\"STUDENTS\",\"title\":\"Notice\",\"message\":\"Plain text\"}";
        Authentication admin = authFor(UUID.randomUUID(), ApplicationRole.ADMIN);
        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .with(authentication(admin)).with(csrf())
                        .header("Idempotency-Key", "admin-students-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("STUDENTS"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recipientCount").value(2))
                .andExpect(jsonPath("$.notificationCount").value(2))
                .andExpect(jsonPath("$.deliveryQueuedCount").value(1));

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .with(authentication(admin)).with(csrf())
                        .header("Idempotency-Key", "admin-students-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationCount").value(2));

        assertThat(broadcastRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    void lecturerBroadcastDeduplicatesStudentAcrossOwnedCourses() throws Exception {
        Lecturer owner = lecturer("owner");
        Student shared = student("shared");
        Student other = student("other");
        Course firstCourse = course(owner, "C1");
        Course secondCourse = course(owner, "C2");
        membership(firstCourse, shared, "A");
        membership(secondCourse, shared, "B");
        membership(secondCourse, other, "C");

        String request = "{\"courseIds\":[\"" + firstCourse.getId() + "\",\""
                + secondCourse.getId() + "\",\"" + firstCourse.getId()
                + "\"],\"title\":\"Reminder\",\"message\":\"Read this\"}";
        mockMvc.perform(post("/api/v1/courses/notifications/broadcast")
                        .with(authentication(authFor(owner.getId(), ApplicationRole.LECTURER))).with(csrf())
                        .header("Idempotency-Key", "lecturer-courses-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("COURSE_STUDENTS"))
                .andExpect(jsonPath("$.recipientCount").value(2))
                .andExpect(jsonPath("$.notificationCount").value(2))
                .andExpect(jsonPath("$.deliveryQueuedCount").value(0));

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void lecturerCannotPartiallyBroadcastForeignCourseAndStudentCannotUseAdminRoute() throws Exception {
        Lecturer owner = lecturer("owner");
        Lecturer foreignOwner = lecturer("foreign");
        Course own = course(owner, "OWN");
        Course foreign = course(foreignOwner, "FOREIGN");
        String request = "{\"courseIds\":[\"" + own.getId() + "\",\"" + foreign.getId()
                + "\"],\"title\":\"Notice\",\"message\":\"Plain text\"}";

        mockMvc.perform(post("/api/v1/courses/notifications/broadcast")
                        .with(authentication(authFor(owner.getId(), ApplicationRole.LECTURER))).with(csrf())
                        .header("Idempotency-Key", "foreign-course")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        assertThat(broadcastRepository.count()).isZero();

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .with(authentication(authFor(UUID.randomUUID(), ApplicationRole.STUDENT))).with(csrf())
                        .header("Idempotency-Key", "student-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"STUDENTS\",\"title\":\"N\",\"message\":\"M\"}"))
                .andExpect(status().isForbidden());
    }

    private Student student(String code) {
        return studentRepository.save(Student.builder()
                .studentCode(code + UUID.randomUUID())
                .email(code + UUID.randomUUID() + "@test.invalid")
                .fullName(code)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Lecturer lecturer(String name) {
        return lecturerRepository.save(Lecturer.builder()
                .email(name + UUID.randomUUID() + "@test.invalid")
                .fullName(name)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Course course(Lecturer owner, String code) {
        return courseRepository.save(Course.builder()
                .instructor(owner).courseCode(code + UUID.randomUUID()).name(code).build());
    }

    private void membership(Course course, Student student, String name) {
        Team team = teamRepository.save(Team.builder().course(course).name(name).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).student(student).roleInTeam(RoleInTeam.MEMBER).build());
    }

    private Authentication authFor(UUID profileId, ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-broadcast-subject",
                role.name().toLowerCase() + "@test.invalid",
                role.name(), role, profileId, AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
