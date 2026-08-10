package com.saga.be.service;

import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationType;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class StudentInvitationEmailComposer {

    private final StudentInvitationProperties properties;

    public StudentInvitationEmailComposer(StudentInvitationProperties properties) {
        this.properties = properties;
    }

    public StudentInvitationMessage compose(
            StudentCourseInvitation invitation,
            List<String> teamNames
    ) {
        String courseName = invitation.getCourse().getName();
        URI loginUri = properties.loginUri();
        boolean linkedStudent = invitation.getInvitationType()
                == StudentInvitationType.LINKED_STUDENT;
        String teamText = teamNames.isEmpty()
                ? ""
                : "\nNhóm: " + String.join(", ", teamNames) + ".";
        String instruction = linkedStudent
                ? "Hồ sơ SAGA của bạn đã được liên kết. Hãy đăng nhập để xem khóa học"
                        + " và thông tin nhóm của bạn."
                : "Một hồ sơ sinh viên SAGA đã tồn tại cho bạn nhưng tài khoản chưa được "
                        + "kích hoạt hoặc liên kết. Hãy đăng ký hoặc đăng nhập bằng chính xác "
                        + "địa chỉ email đã nhận thư này. Trong lần đăng nhập đầu tiên, quy trình "
                        + "hiện có của SAGA sẽ liên kết tài khoản với hồ sơ.";
        String callToAction = linkedStudent
                ? "Đăng nhập SAGA"
                : "Đăng ký / Kích hoạt tài khoản SAGA";
        String body = "Xin chào,\n\nBạn đã được thêm vào khóa học " + courseName + "."
                + teamText
                + "\n\n" + instruction
                + "\n\n" + callToAction + ": " + loginUri;
        String htmlBody = htmlBody(
                courseName,
                teamNames,
                instruction,
                callToAction,
                loginUri
        );
        return new StudentInvitationMessage(
                invitation.getStudent().getEmail(),
                "[SAGA] Bạn đã được thêm vào khóa học " + courseName,
                body,
                htmlBody,
                invitation.getInvitationType(),
                courseName,
                List.copyOf(teamNames),
                loginUri,
                invitation.getAttemptCount()
        );
    }

    private String htmlBody(
            String courseName,
            List<String> teamNames,
            String instruction,
            String callToAction,
            URI loginUri
    ) {
        String escapedCourseName = HtmlUtils.htmlEscape(courseName);
        String escapedInstruction = HtmlUtils.htmlEscape(instruction);
        String escapedCallToAction = HtmlUtils.htmlEscape(callToAction);
        String escapedLoginUrl = HtmlUtils.htmlEscape(loginUri.toString());
        String teamParagraph = teamNames.isEmpty()
                ? ""
                : "<p><strong>Nhóm:</strong> "
                        + teamNames.stream()
                        .map(HtmlUtils::htmlEscape)
                        .reduce((first, second) -> first + ", " + second)
                        .orElse("")
                        + ".</p>";
        return "<html><body>"
                + "<p>Xin chào,</p>"
                + "<p>Bạn đã được thêm vào khóa học <strong>" + escapedCourseName
                + "</strong>.</p>"
                + teamParagraph
                + "<p>" + escapedInstruction + "</p>"
                + "<p><a href=\"" + escapedLoginUrl + "\">" + escapedCallToAction
                + "</a></p>"
                + "</body></html>";
    }
}
