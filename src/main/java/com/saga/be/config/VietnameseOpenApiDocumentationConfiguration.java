package com.saga.be.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** Chỉ chuẩn hóa metadata OpenAPI, không tham gia xử lý HTTP hay phân quyền. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class VietnameseOpenApiDocumentationConfiguration {

    private static final Map<String, Documentation> SPECIAL_OPERATIONS = Map.ofEntries(
            entry("ProjectDetailController#dashboardStats", "Xem thống kê tổng quan dự án", "Chỉ đọc snapshot local; không gọi Jira hoặc GitHub. Task đã tombstone không được tính và task hoàn thành dùng trạng thái DONE."),
            entry("ProjectGitHubReadController#branches", "Lấy danh sách nhánh GitHub", "Repository phải thuộc đúng Project. Backend dùng installation credential nội bộ, frontend không gửi GitHub token. Endpoint chỉ đọc; phân trang bắt đầu từ 0."),
            entry("ProjectGitHubReadController#commits", "Lấy commit theo nhánh GitHub", "Tên branch nằm ở query parameter và có thể chứa dấu '/'; frontend phải URL-encode giá trị. Không trả credential hoặc raw provider payload."),
            entry("ProjectIntegrationController#githubRepositoryReconnect", "Kết nối lại repository GitHub", "Chỉ Project Manager được thực hiện. Mutation yêu cầu CSRF và trả 202; backend dùng initial backfill cùng cơ chế claim/coalesce hiện có. Frontend không gửi token hoặc installation ID."),
            entry("ProjectIntegrationController#syncHistory", "Xem lịch sử đồng bộ dự án", "Lịch sử được phân trang và lọc theo targetSystem, status hoặc jobType. Chỉ trả dữ liệu job đã được làm sạch trong phạm vi Project."),
            entry("ProjectIntegrationController#sync", "Yêu cầu đồng bộ thủ công", "Chỉ Project Manager được thực hiện. Tác vụ dùng claim/coalesce và có thể chạy nền; không yêu cầu Idempotency-Key."),
            entry("ProjectSprintController#create", "Tạo Sprint mới trên Jira", "Jira là source of truth. Request bắt buộc Idempotency-Key; startDate/endDate là ISO-8601 có Z hoặc offset, backend không cộng cứng UTC+7. Thành công được canonical fetch rồi upsert local; nhiều Scrum board có thể trả conflict yêu cầu chọn board."),
            entry("ProjectSprintController#update", "Cập nhật Sprint trên Jira", "Jira là source of truth. Request bắt buộc Idempotency-Key; thời gian dùng ISO-8601 có Z hoặc offset và backend không cộng cứng UTC+7."),
            entry("ProjectSprintController#start", "Bắt đầu Sprint trên Jira", "Mutation Jira yêu cầu Idempotency-Key. Backend đồng bộ canonical state về local sau khi nhà cung cấp xác nhận."),
            entry("ProjectSprintController#close", "Đóng Sprint trên Jira", "Mutation Jira yêu cầu Idempotency-Key. Backend đồng bộ canonical state về local sau khi nhà cung cấp xác nhận."),
            entry("ProjectSprintController#delete", "Xóa Sprint trên Jira", "Mutation Jira yêu cầu Idempotency-Key. Backend không blind retry khi kết quả remote chưa rõ."),
            entry("WebhookController#github", "Nhận webhook từ GitHub", "Endpoint dành cho GitHub, không dành cho frontend. Xác thực bằng cơ chế chữ ký riêng và được miễn CSRF."),
            entry("WebhookController#jira", "Nhận webhook từ Jira", "Endpoint dành cho Jira, không dành cho frontend. Xác thực bằng cơ chế riêng và được miễn CSRF."),
            entry("PrivacyPolicyController#getPrivacyPolicy", "Xem chính sách riêng tư", "Endpoint công khai, chỉ trả nội dung chính sách và thông tin liên hệ công khai."),
            entry("AuthController#login", "Bắt đầu đăng nhập", "Endpoint công khai trả redirect sang luồng đăng nhập Cognito. FE mở bằng browser navigation, không gọi như JSON API và không dùng Bearer token."),
            entry("AuthController#me", "Xem thông tin tài khoản đang đăng nhập", "Trả hồ sơ, vai trò và trạng thái của người dùng từ browser session SAGA. Không truyền userId và không dùng Bearer token."),
            entry("AuthController#csrf", "Lấy thông tin CSRF", "Trả token CSRF gắn với browser session hiện tại. FE gửi token bằng header X-XSRF-TOKEN cho request thay đổi dữ liệu."),
            entry("MyNotificationController#list", "Xem danh sách thông báo của tôi", "Trả lịch sử Notification Bell của chính tài khoản đang đăng nhập, mới nhất trước. page bắt đầu từ 0, size từ 1 đến 100; SAGA DB là nguồn dữ liệu chuẩn."),
            entry("MyNotificationController#unreadCount", "Đếm số thông báo chưa đọc", "Đếm thông báo chưa đọc của chính tài khoản đang đăng nhập từ SAGA DB."),
            entry("MyNotificationController#markRead", "Đánh dấu thông báo là đã đọc", "Chỉ cập nhật thông báo thuộc tài khoản đang đăng nhập. Gọi lại với thông báo đã đọc không tạo thêm thay đổi."),
            entry("MyFirebaseInstallationController#register", "Đăng ký trình duyệt hiện tại để nhận thông báo đẩy", "Đăng ký hoặc kích hoạt lại Firebase Installation ID thuộc tài khoản đang đăng nhập. FID là chuỗi opaque của Firebase Web SDK; không gửi Firebase Admin credential."),
            entry("MyFirebaseInstallationController#unregister", "Ngừng nhận thông báo đẩy trên trình duyệt này", "Thu hồi installation thuộc tài khoản đang đăng nhập. Notification Bell trong SAGA DB vẫn là lịch sử chuẩn sau khi ngừng FCM trên thiết bị."),
            entry("AdminNotificationBroadcastController#broadcast", "Gửi thông báo đến một nhóm người dùng", "Chỉ ADMIN. audience xác định STUDENTS, LECTURERS hoặc ALL_USERS theo profile local hiện tại; request cần Idempotency-Key và backend lưu Bell trước khi xếp hàng FCM."),
            entry("CourseNotificationBroadcastController#broadcast", "Gửi thông báo đến sinh viên của các khóa học đang giảng dạy", "Chỉ LECTURER và chỉ chấp nhận các Course active do chính giảng viên phụ trách. Người nhận được lấy từ TeamMember, course và người nhận trùng được loại; request cần Idempotency-Key."),
            entry("CourseController#importStudents", "Import sinh viên và phân nhóm trong khóa học", "ADMIN hoặc giảng viên phụ trách Course tải file XLSX đúng mẫu. Sau khi membership được tạo thành công, backend enqueue email mời bất đồng bộ; lỗi gửi email không rollback import và FE không gọi API gửi mail riêng."),
            entry("CourseController#importStudentsByAdminTemplate", "Import sinh viên vào khóa học bằng mẫu Admin", "Chỉ ADMIN tải file XLSX theo mẫu 5 cột. Backend enqueue email mời bất đồng bộ sau khi dữ liệu import được lưu; lỗi gửi email không rollback import và không có API SMTP/send-mail cho FE."),
            entry("TeamContributionController#getContributionEvaluation", "Xem đánh giá đóng góp hiện tại của nhóm", "ADMIN xem mọi Team. LECTURER chỉ xem Team thuộc Course mình phụ trách. STUDENT chỉ xem khi có đúng TeamMember role LEADER của chính Team đang yêu cầu; MEMBER và MENTOR không có quyền. Response chỉ chứa định danh học vụ tối thiểu và metric Contribution, không chứa email, Cognito subject, reviewer/comment, token, credential hoặc raw provider payload."),
            entry("PersonalIntegrationController#connections", "Xem trạng thái liên kết Jira và GitHub của tôi", "Trả trạng thái liên kết Jira/GitHub của tài khoản đang đăng nhập; không trả access token hoặc provider credential."),
            entry("PersonalIntegrationController#connectJira", "Bắt đầu liên kết tài khoản Jira", "Trả redirect để browser đi vào luồng ủy quyền Jira. FE dùng browser navigation và không gửi Jira token."),
            entry("PersonalIntegrationController#connectGitHub", "Bắt đầu liên kết tài khoản GitHub", "Trả redirect để browser đi vào luồng ủy quyền GitHub. FE dùng browser navigation và không gửi GitHub token."),
            entry("PersonalIntegrationController#githubCallback", "Hoàn tất callback liên kết GitHub", "Callback được provider/browser redirect tới; FE không gọi endpoint này như REST API thông thường. Backend lưu kết quả ngắn hạn rồi redirect về frontend."),
            entry("JiraIntegrationCallbackController#callback", "Hoàn tất callback liên kết Jira", "Callback được Jira/browser redirect tới; FE không gọi endpoint này như REST API thông thường. Backend lưu kết quả ngắn hạn rồi redirect về frontend."),
            entry("ProjectIntegrationCallbackController#githubSetup", "Nhận callback cài đặt GitHub App", "Callback được GitHub/browser redirect tới; FE không gọi endpoint này như REST API thông thường."),
            entry("ProjectIntegrationCallbackController#githubCallback", "Hoàn tất callback liên kết GitHub cho dự án", "Callback được GitHub/browser redirect tới; FE không gọi endpoint này như REST API thông thường. Backend lưu kết quả ngắn hạn rồi redirect về frontend."),
            entry("IntegrationCallbackResultController#consume", "Nhận kết quả liên kết tài khoản sau callback", "FE gọi một lần sau khi browser quay về từ provider để nhận kết quả đã lưu theo session hiện tại. resultId không phải provider token."),
            entry("ProjectIntegrationController#jiraConnect", "Bắt đầu liên kết Jira cho dự án", "Trả redirect để browser đi vào luồng ủy quyền Jira của Project. FE dùng browser navigation và không gửi Jira token."),
            entry("ProjectIntegrationController#githubInstall", "Bắt đầu cài đặt GitHub App cho dự án", "Trả redirect để browser đi vào luồng cài đặt GitHub App. FE dùng browser navigation và không gửi installation credential."),
            entry("ProjectIntegrationController#githubSetup", "Nhận kết quả cài đặt GitHub App cho dự án", "Callback được GitHub/browser redirect tới; FE không gọi endpoint này như REST API thông thường."),
            entry("ProjectIntegrationController#githubCallback", "Hoàn tất liên kết GitHub cho dự án", "Callback được GitHub/browser redirect tới; FE không gọi endpoint này như REST API thông thường. Backend lưu kết quả ngắn hạn rồi redirect về frontend.")
    );

    private static final Map<String, String> SUMMARY_OVERRIDES = Map.ofEntries(
            summary("AdminActiveSemesterController#current", "Xem học kỳ mặc định của hệ thống"),
            summary("AdminActiveSemesterController#updateActiveSemester", "Đặt học kỳ mặc định của hệ thống"),
            summary("AdminCourseReportController#export", "Tải báo cáo dữ liệu khóa học"),
            summary("AdminNotificationBroadcastController#broadcast", "Gửi thông báo đến một nhóm người dùng"),
            summary("AdminReadController#users", "Xem danh sách người dùng"),
            summary("AdminReadController#updateUserStatus", "Cập nhật trạng thái tài khoản người dùng"),
            summary("AdminReadController#auditLogs", "Xem nhật ký hệ thống"),
            summary("AdminReadController#systemStats", "Xem thống kê tổng quan hệ thống"),
            summary("AdminReadController#integrationHealth", "Xem trạng thái tích hợp Jira và GitHub"),
            summary("AdminReadController#teams", "Xem danh sách nhóm toàn hệ thống"),
            summary("AdminReadController#projects", "Xem danh sách dự án toàn hệ thống"),
            summary("AdminReadController#courseProgressOverview", "Xem tổng quan tiến độ các khóa học"),
            summary("AdminUserImportController#importUsers", "Import hồ sơ người dùng toàn hệ thống"),
            summary("AuthController#login", "Bắt đầu đăng nhập"),
            summary("AuthController#me", "Xem thông tin tài khoản đang đăng nhập"),
            summary("AuthController#csrf", "Lấy thông tin CSRF"),
            summary("ClassController#getClassById", "Xem chi tiết lớp học"),
            summary("ClassController#createClass", "Tạo lớp học"),
            summary("ClassController#updateClass", "Cập nhật lớp học"),
            summary("ClassController#deleteClass", "Xóa lớp học"),
            summary("ClassController#getClasses", "Xem danh sách lớp học"),
            summary("CourseContributionWeightController#getCurrentWeights", "Xem cấu hình trọng số đóng góp"),
            summary("CourseContributionWeightController#requestWeightChange", "Đề nghị thay đổi trọng số đóng góp"),
            summary("CourseContributionWeightController#listWeightChangeRequests", "Xem các đề nghị thay đổi trọng số"),
            summary("CourseContributionWeightController#decideWeightChangeRequest", "Duyệt đề nghị thay đổi trọng số"),
            summary("CourseController#getCourseById", "Xem chi tiết khóa học"),
            summary("CourseController#createCourse", "Tạo khóa học"),
            summary("CourseController#updateCourse", "Cập nhật khóa học"),
            summary("CourseController#deleteCourse", "Xóa khóa học"),
            summary("CourseController#getCourses", "Xem danh sách khóa học"),
            summary("CourseController#getLecturersForCourseAssignment", "Xem giảng viên có thể phụ trách khóa học"),
            summary("CourseController#getCourseStudents", "Xem danh sách sinh viên của khóa học"),
            summary("CourseController#getCourseStudent", "Xem thông tin sinh viên trong khóa học"),
            summary("CourseController#importStudents", "Import sinh viên và phân nhóm trong khóa học"),
            summary("CourseController#importStudentsByAdminTemplate", "Import sinh viên vào khóa học bằng mẫu Admin"),
            summary("CourseController#downloadStudentsGroupingTemplate", "Tải mẫu phân nhóm sinh viên"),
            summary("CourseController#downloadAdminStudentsTemplate", "Tải mẫu Admin thêm sinh viên"),
            summary("CourseController#addStudentManually", "Thêm một sinh viên vào khóa học"),
            summary("CourseController#updateCourseStudent", "Cập nhật nhóm của sinh viên trong khóa học"),
            summary("CourseController#removeStudentFromCourse", "Xóa sinh viên khỏi khóa học"),
            summary("CourseNotificationBroadcastController#broadcast", "Gửi thông báo đến sinh viên của các khóa học đang giảng dạy"),
            summary("IdentityMappingReviewController#mappings", "Xem danh sách ánh xạ danh tính"),
            summary("IdentityMappingReviewController#review", "Duyệt một ánh xạ danh tính"),
            summary("IntegrationCallbackResultController#consume", "Nhận kết quả liên kết tài khoản sau callback"),
            summary("JiraIntegrationCallbackController#callback", "Hoàn tất callback liên kết Jira"),
            summary("LecturerAnalyticsController#teamDetail", "Xem tổng quan hoạt động của nhóm"),
            summary("LecturerAnalyticsController#progress", "Xem tiến độ của sinh viên"),
            summary("LecturerAnalyticsController#activities", "Xem dòng thời gian hoạt động của sinh viên"),
            summary("LecturerAnalyticsController#contributionDetail", "Xem chi tiết đóng góp của sinh viên"),
            summary("LecturerAnalyticsController#earlyWarnings", "Xem cảnh báo sớm của khóa học"),
            summary("LecturerAnalyticsController#interactions", "Xem tương tác giữa các thành viên nhóm"),
            summary("LecturerAnalyticsController#heatmap", "Xem bản đồ nhiệt hoạt động của nhóm"),
            summary("LecturerAnalyticsController#overview", "Xem tổng quan hoạt động theo ngày của nhóm"),
            summary("LecturerAnalyticsController#velocity", "Xem vận tốc Sprint của nhóm"),
            summary("MyCourseTeamController#getMyCourseTeamMembers", "Xem thành viên nhóm của tôi trong khóa học"),
            summary("MyFirebaseInstallationController#register", "Đăng ký trình duyệt hiện tại để nhận thông báo đẩy"),
            summary("MyFirebaseInstallationController#unregister", "Ngừng nhận thông báo đẩy trên trình duyệt này"),
            summary("MyNotificationController#list", "Xem danh sách thông báo của tôi"),
            summary("MyNotificationController#unreadCount", "Đếm số thông báo chưa đọc"),
            summary("MyNotificationController#markRead", "Đánh dấu thông báo là đã đọc"),
            summary("PeerReviewController#submit", "Gửi đánh giá chéo trong Sprint"),
            summary("PeerReviewController#getCandidates", "Xem thành viên có thể được đánh giá"),
            summary("PeerReviewController#getSprintReviews", "Xem các đánh giá chéo của Sprint"),
            summary("PeerReviewDefaultRubricController#getDefaultRubric", "Xem rubric mặc định"),
            summary("PeerReviewRubricController#getRubric", "Xem rubric đánh giá của nhóm"),
            summary("PersonalIntegrationController#connections", "Xem trạng thái liên kết Jira và GitHub của tôi"),
            summary("PersonalIntegrationController#connectJira", "Bắt đầu liên kết tài khoản Jira"),
            summary("PersonalIntegrationController#disconnectJira", "Ngắt liên kết tài khoản Jira"),
            summary("PersonalIntegrationController#connectGitHub", "Bắt đầu liên kết tài khoản GitHub"),
            summary("PersonalIntegrationController#githubCallback", "Hoàn tất callback liên kết GitHub"),
            summary("PersonalIntegrationController#disconnectGitHub", "Ngắt liên kết tài khoản GitHub"),
            summary("PrivacyPolicyController#getPrivacyPolicy", "Xem chính sách riêng tư"),
            summary("ProjectDetailController#dashboardStats", "Xem thống kê tổng quan dự án"),
            summary("ProjectDetailController#get", "Xem thông tin chi tiết dự án"),
            summary("ProjectDetailController#update", "Cập nhật thông tin dự án"),
            summary("ProjectGitHubReadController#branches", "Xem danh sách nhánh GitHub"),
            summary("ProjectGitHubReadController#commits", "Xem commit theo nhánh GitHub"),
            summary("ProjectIntegrationCallbackController#githubSetup", "Nhận callback cài đặt GitHub App"),
            summary("ProjectIntegrationCallbackController#githubCallback", "Hoàn tất callback liên kết GitHub cho dự án"),
            summary("ProjectIntegrationController#integrations", "Xem trạng thái tích hợp của dự án"),
            summary("ProjectIntegrationController#jiraConnect", "Bắt đầu liên kết Jira cho dự án"),
            summary("ProjectIntegrationController#jiraLink", "Xác nhận Jira Project dùng cho dự án"),
            summary("ProjectIntegrationController#jiraDisconnect", "Ngắt liên kết Jira khỏi dự án"),
            summary("ProjectIntegrationController#githubInstall", "Bắt đầu cài đặt GitHub App cho dự án"),
            summary("ProjectIntegrationController#githubSetup", "Nhận kết quả cài đặt GitHub App cho dự án"),
            summary("ProjectIntegrationController#githubCallback", "Hoàn tất liên kết GitHub cho dự án"),
            summary("ProjectIntegrationController#githubRepositories", "Liên kết repository GitHub với dự án"),
            summary("ProjectIntegrationController#githubRepositoryDisconnect", "Ngắt repository GitHub khỏi dự án"),
            summary("ProjectIntegrationController#syncStatus", "Xem trạng thái đồng bộ dự án"),
            summary("ProjectIntegrationController#syncHistory", "Xem lịch sử đồng bộ dự án"),
            summary("ProjectIntegrationController#githubRepositoryReconnect", "Kết nối lại repository GitHub"),
            summary("ProjectIntegrationController#sync", "Yêu cầu đồng bộ dự án"),
            summary("ProjectSprintController#detail", "Xem chi tiết Sprint"),
            summary("ProjectSprintController#create", "Tạo Sprint mới trên Jira"),
            summary("ProjectSprintController#update", "Cập nhật Sprint trên Jira"),
            summary("ProjectSprintController#start", "Bắt đầu Sprint trên Jira"),
            summary("ProjectSprintController#close", "Đóng Sprint trên Jira"),
            summary("ProjectSprintController#delete", "Xóa Sprint trên Jira"),
            summary("ProjectSprintController#getProjectSprints", "Xem danh sách Sprint của dự án"),
            summary("ProjectSprintController#getTeamSprints", "Xem danh sách Sprint của nhóm"),
            summary("ProjectTaskReadController#createTask", "Tạo Task mới trên Jira"),
            summary("ProjectTaskReadController#updateTask", "Cập nhật Task trên Jira"),
            summary("ProjectTaskReadController#transitions", "Xem các trạng thái Task có thể chuyển tới"),
            summary("ProjectTaskReadController#transition", "Chuyển trạng thái Task trên Jira"),
            summary("ProjectTaskReadController#assignee", "Thay đổi người được giao Task"),
            summary("ProjectTaskReadController#sprint", "Chuyển Task vào Sprint hoặc backlog"),
            summary("ProjectTaskReadController#estimate", "Cập nhật Story Point của Task"),
            summary("ProjectTaskReadController#delete", "Xóa Task trên Jira"),
            summary("ProjectTaskReadController#getTasks", "Xem danh sách Task của dự án"),
            summary("ProjectTaskReadController#getTask", "Xem chi tiết Task"),
            summary("SemesterController#getSemesterById", "Xem chi tiết học kỳ"),
            summary("SemesterController#createSemester", "Tạo học kỳ"),
            summary("SemesterController#updateSemester", "Cập nhật học kỳ"),
            summary("SemesterController#deleteSemester", "Xóa học kỳ"),
            summary("SemesterController#getSemesters", "Xem danh sách học kỳ"),
            summary("SubjectController#getSubjectById", "Xem chi tiết môn học"),
            summary("SubjectController#createSubject", "Tạo môn học"),
            summary("SubjectController#updateSubject", "Cập nhật môn học"),
            summary("SubjectController#deleteSubject", "Xóa môn học"),
            summary("SubjectController#getSubjects", "Xem danh sách môn học"),
            summary("TeamContributionController#getContributionEvaluation", "Xem đánh giá đóng góp của nhóm"),
            summary("TeamContributionController#requestContributionOverride", "Đề nghị điều chỉnh đóng góp của nhóm"),
            summary("TeamProjectController#create", "Tạo dự án cho nhóm"),
            summary("TeamRosterController#getMembers", "Xem danh sách thành viên của nhóm trong khóa học"),
            summary("WebhookController#github", "Nhận webhook từ GitHub"),
            summary("WebhookController#jira", "Nhận webhook từ Jira")
    );

    @Bean
    OperationCustomizer vietnameseOperationCustomizer() {
        return (operation, handlerMethod) -> document(operation, handlerMethod);
    }

    @Bean
    OpenApiCustomizer vietnameseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (isBlank(schema.getDescription())) {
                    schema.setDescription(schemaDescription(name));
                }
                documentSchemaProperties(name, schema);
            });
        };
    }

    private Operation document(Operation operation, HandlerMethod handlerMethod) {
        String controller = handlerMethod.getBeanType().getSimpleName();
        String key = controller + "#" + handlerMethod.getMethod().getName();
        Documentation documentation = SPECIAL_OPERATIONS.getOrDefault(key, generic(handlerMethod));
        operation.setTags(List.of(tagFor(controller)));
        operation.setSummary(documentation.summary());
        operation.setDescription(documentation.description() + sessionAndCsrfNote(handlerMethod, operation));
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(this::documentParameter);
        }
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((code, response) -> documentResponse(code, response));
        }
        return operation;
    }

    private Documentation generic(HandlerMethod method) {
        String controller = method.getBeanType().getSimpleName();
        String key = controller + "#" + method.getMethod().getName();
        String tag = tagFor(controller);
        String summary = SUMMARY_OVERRIDES.getOrDefault(key, "Xem thông tin " + tag.toLowerCase(Locale.ROOT));
        return new Documentation(tag, summary, scopeDescription(controller));
    }

    private String sessionAndCsrfNote(HandlerMethod method, Operation operation) {
        String name = method.getMethod().getName();
        String controller = method.getBeanType().getSimpleName();
        if (controller.equals("WebhookController")
                || method.getBeanType().getSimpleName().equals("PrivacyPolicyController")
                || name.equals("login") || isProviderCallback(controller, name)) {
            return "";
        }
        boolean mutation = isMutation(method);
        String note = mutation
                ? " Yêu cầu đăng nhập bằng browser session. Mutation yêu cầu CSRF và Swagger UI tự gắn token."
                : " Yêu cầu đăng nhập bằng browser session.";
        if (hasIdempotencyKey(operation)) {
            note += " Request phải có Idempotency-Key; retry cùng intent dùng lại cùng key.";
        }
        return note;
    }

    private void documentParameter(Parameter parameter) {
        if (!isBlank(parameter.getDescription())) {
            return;
        }
        String name = parameter.getName();
        switch (name) {
            case "projectId" -> parameter.setDescription("UUID của Project trong SAGA, không phải Jira Project ID.");
            case "repositoryId" -> parameter.setDescription("Mã repository GitHub đã được liên kết trong đúng Project.");
            case "teamId" -> parameter.setDescription("UUID của nhóm trong SAGA.");
            case "courseId" -> parameter.setDescription("UUID của khóa học trong SAGA.");
            case "studentId" -> parameter.setDescription("UUID của sinh viên trong SAGA.");
            case "sprintId" -> parameter.setDescription("UUID Sprint local trong phạm vi hợp lệ.");
            case "taskId" -> parameter.setDescription("UUID Task local trong phạm vi Project.");
            case "page" -> parameter.setDescription("Trang bắt đầu từ 0.");
            case "size" -> parameter.setDescription("Số phần tử mỗi trang, theo giới hạn validation của Backend.");
            case "branch" -> parameter.setDescription("Tên nhánh GitHub, có thể chứa '/'; ví dụ feature/project-setup.");
            case "provider" -> parameter.setDescription("Nhà cung cấp cần đồng bộ: JIRA, GITHUB hoặc ALL.");
            case "Idempotency-Key" -> parameter.setDescription("UUID đại diện cho một intent mutation; retry cùng request dùng lại cùng key.");
            default -> parameter.setDescription("Tham số " + name + " của request.");
        }
    }

    private void documentResponse(String code, ApiResponse response) {
        if (!isBlank(response.getDescription())) {
            return;
        }
        response.setDescription(switch (code) {
            case "200" -> "Thành công.";
            case "201" -> "Tạo thành công.";
            case "202" -> "Tác vụ đã được chấp nhận xử lý nền.";
            case "204" -> "Hoàn tất, không có nội dung trả về.";
            default -> "Kết quả theo contract hiện có của Backend.";
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void documentSchemaProperties(String schemaName, Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }
        ((Map<String, Schema>) schema.getProperties()).forEach((name, property) -> {
            if (isBlank(property.getDescription())) {
                property.setDescription(schemaPropertyDescription(schemaName, name));
            }
            if ("FirebaseInstallationRegistrationRequest".equals(schemaName)
                    && "firebaseInstallationId".equals(name)) {
                property.setExample("example-browser-fid");
            }
        });
    }

    private String tagFor(String controller) {
        return switch (controller) {
            case "AuthController" -> "Xác thực";
            case "ClassController" -> "Lớp học";
            case "CourseController" -> "Khóa học";
            case "CourseContributionWeightController", "TeamContributionController" -> "Đóng góp";
            case "SemesterController" -> "Học kỳ";
            case "AdminActiveSemesterController", "AdminCourseReportController",
                    "AdminReadController", "AdminUserImportController" -> "Quản trị";
            case "SubjectController" -> "Môn học";
            case "MyCourseTeamController", "TeamRosterController" -> "Nhóm";
            case "TeamProjectController", "ProjectDetailController" -> "Dự án";
            case "ProjectIntegrationController", "ProjectIntegrationCallbackController" -> "Tích hợp dự án";
            case "ProjectGitHubReadController" -> "GitHub";
            case "ProjectTaskReadController" -> "Jira Task";
            case "ProjectSprintController" -> "Jira Sprint";
            case "PersonalIntegrationController" -> "Tích hợp cá nhân";
            case "AdminNotificationBroadcastController", "CourseNotificationBroadcastController",
                    "MyFirebaseInstallationController", "MyNotificationController" -> "Thông báo";
            case "IdentityMappingReviewController", "IntegrationCallbackResultController", "JiraIntegrationCallbackController" -> "Đồng bộ dữ liệu";
            case "LecturerAnalyticsController", "PeerReviewController", "PeerReviewDefaultRubricController", "PeerReviewRubricController" -> "Đánh giá";
            case "WebhookController" -> "Webhook";
            case "PrivacyPolicyController" -> "Chính sách riêng tư";
            default -> "Dự án";
        };
    }

    private String vietnameseSchemaName(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private String schemaDescription(String name) {
        return switch (name) {
            case "FirebaseInstallationRegistrationRequest" -> "Yêu cầu đăng ký Firebase Installation của trình duyệt hiện tại.";
            case "FirebaseInstallationResponse" -> "Trạng thái đăng ký nhận thông báo đẩy của một trình duyệt thuộc user hiện tại.";
            case "NotificationResponse" -> "Một thông báo Bell thuộc user đang đăng nhập.";
            case "NotificationUnreadCountResponse" -> "Tổng số thông báo chưa đọc của user đang đăng nhập.";
            case "NotificationBroadcastRequest" -> "Nội dung phát thông báo thủ công của ADMIN.";
            case "CourseNotificationBroadcastRequest" -> "Nội dung phát thông báo của LECTURER đến các Course được phép.";
            case "NotificationBroadcastResponse" -> "Kết quả fanout Notification Bell và số delivery FCM đã xếp hàng.";
            default -> "Schema dữ liệu " + vietnameseSchemaName(name) + " của SAGA.";
        };
    }

    private String schemaPropertyDescription(String schemaName, String propertyName) {
        String key = schemaName + "." + propertyName;
        return switch (key) {
            case "FirebaseInstallationRegistrationRequest.firebaseInstallationId" -> "Firebase Installation ID opaque do Firebase Web SDK cấp cho trình duyệt; tối đa 255 ký tự.";
            case "FirebaseInstallationResponse.id" -> "UUID installation trong SAGA, dùng khi gọi API revoke.";
            case "FirebaseInstallationResponse.active" -> "true nếu trình duyệt vẫn được phép nhận FCM.";
            case "FirebaseInstallationResponse.lastRegisteredAt" -> "Thời điểm đăng ký hoặc kích hoạt gần nhất.";
            case "FirebaseInstallationResponse.revokedAt" -> "Thời điểm revoke; null khi installation còn active.";
            case "NotificationResponse.id" -> "UUID thông báo dùng để đánh dấu đã đọc.";
            case "NotificationResponse.type" -> "Loại sự kiện notification do backend tạo.";
            case "NotificationResponse.title" -> "Tiêu đề hiển thị trong Notification Bell.";
            case "NotificationResponse.message" -> "Nội dung thông báo dạng plain text.";
            case "NotificationResponse.actionUrl" -> "Đường dẫn nội bộ đã được backend xác nhận; hiện có thể null.";
            case "NotificationResponse.read" -> "true nếu user đã đánh dấu thông báo là đã đọc.";
            case "NotificationResponse.readAt" -> "Thời điểm đọc; null khi chưa đọc.";
            case "NotificationResponse.createdAt" -> "Thời điểm Notification Bell được lưu trong SAGA DB.";
            case "NotificationUnreadCountResponse.unreadCount" -> "Số thông báo chưa đọc của user hiện tại.";
            case "NotificationBroadcastRequest.audience" -> "Nhóm nhận: STUDENTS, LECTURERS hoặc ALL_USERS.";
            case "CourseNotificationBroadcastRequest.courseIds" -> "Danh sách 1–100 UUID Course active do Lecturer hiện tại phụ trách.";
            case "NotificationBroadcastRequest.title", "CourseNotificationBroadcastRequest.title" -> "Tiêu đề plain text, tối đa 160 ký tự và không chứa dấu ngoặc HTML.";
            case "NotificationBroadcastRequest.message", "CourseNotificationBroadcastRequest.message" -> "Nội dung plain text, tối đa 1000 ký tự và không chứa dấu ngoặc HTML.";
            case "NotificationBroadcastResponse.audience" -> "Audience đã được backend áp dụng; Course broadcast trả COURSE_STUDENTS.";
            case "NotificationBroadcastResponse.recipientCount" -> "Số người nhận khác nhau sau khi loại trùng.";
            case "NotificationBroadcastResponse.notificationCount" -> "Số Notification Bell đã được lưu.";
            case "NotificationBroadcastResponse.deliveryQueuedCount" -> "Số delivery FCM được xếp hàng cho các installation active.";
            default -> "Trường " + vietnameseSchemaName(propertyName) + ".";
        };
    }

    private String scopeDescription(String controller) {
        return switch (controller) {
            case "AdminActiveSemesterController", "AdminCourseReportController", "AdminReadController",
                    "AdminUserImportController" -> "Chỉ ADMIN; dữ liệu nằm trong phạm vi vận hành toàn hệ thống.";
            case "CourseController", "ClassController", "SemesterController", "SubjectController" ->
                    "Dữ liệu và quyền truy cập được giới hạn theo contract hiện có của tài khoản đang đăng nhập.";
            case "LecturerAnalyticsController", "CourseContributionWeightController" ->
                    "Dành cho giảng viên trong phạm vi Course được phân công; chỉ dùng dữ liệu SAGA đã lưu.";
            case "PeerReviewController", "PeerReviewDefaultRubricController", "PeerReviewRubricController" ->
                    "Dữ liệu được giới hạn theo Team, Sprint và quyền Peer Review hiện có.";
            case "ProjectIntegrationController", "ProjectDetailController", "TeamProjectController" ->
                    "Dữ liệu được giới hạn theo Project và quyền Project Manager hiện có.";
            case "ProjectTaskReadController", "ProjectSprintController" ->
                    "Jira là source of truth; backend xác nhận dữ liệu canonical rồi mới trả kết quả.";
            default -> "Dữ liệu được giới hạn theo phạm vi quyền hiện có của tài khoản đang đăng nhập.";
        };
    }

    private boolean isMutation(HandlerMethod method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (RequestMethod requestMethod : mapping.method()) {
            if (requestMethod != RequestMethod.GET && requestMethod != RequestMethod.HEAD
                    && requestMethod != RequestMethod.OPTIONS) {
                return true;
            }
        }
        return false;
    }

    private boolean isProviderCallback(String controller, String method) {
        return "JiraIntegrationCallbackController".equals(controller)
                || "ProjectIntegrationCallbackController".equals(controller)
                || ("ProjectIntegrationController".equals(controller)
                        && ("githubSetup".equals(method) || "githubCallback".equals(method)))
                || ("PersonalIntegrationController".equals(controller) && "githubCallback".equals(method));
    }

    private boolean hasIdempotencyKey(Operation operation) {
        return operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> "Idempotency-Key".equalsIgnoreCase(parameter.getName()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map.Entry<String, Documentation> entry(String key, String summary, String description) {
        String controller = key.substring(0, key.indexOf('#'));
        return Map.entry(key, new Documentation(tagForStatic(controller), summary, description));
    }

    private static Map.Entry<String, String> summary(String key, String value) {
        return Map.entry(key, value);
    }

    private static String tagForStatic(String controller) {
        return switch (controller) {
            case "ProjectDetailController" -> "Dự án";
            case "ProjectGitHubReadController" -> "GitHub";
            case "ProjectIntegrationController" -> "Tích hợp dự án";
            case "ProjectSprintController" -> "Jira Sprint";
            case "WebhookController" -> "Webhook";
            case "PrivacyPolicyController" -> "Chính sách riêng tư";
            default -> "Xác thực";
        };
    }

    private record Documentation(String tag, String summary, String description) { }
}
