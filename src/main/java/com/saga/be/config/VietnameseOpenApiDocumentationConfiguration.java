package com.saga.be.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

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
            entry("AuthController#login", "Bắt đầu đăng nhập", "Endpoint công khai để điều hướng browser sang luồng đăng nhập; không dùng bearer token."),
            entry("AuthController#csrf", "Lấy thông tin CSRF", "Dùng với browser session. Swagger UI tự bootstrap và gắn CSRF cho mutation cùng origin.")
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
                    schema.setDescription("Schema dữ liệu " + vietnameseSchemaName(name) + " của SAGA.");
                }
                documentSchemaProperties(schema);
            });
        };
    }

    private Operation document(Operation operation, HandlerMethod handlerMethod) {
        String key = handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        Documentation documentation = SPECIAL_OPERATIONS.getOrDefault(key, generic(handlerMethod));
        operation.setTags(List.of(documentation.tag()));
        operation.setSummary(documentation.summary());
        operation.setDescription(documentation.description() + sessionAndCsrfNote(handlerMethod));
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(this::documentParameter);
        }
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((code, response) -> documentResponse(code, response));
        }
        return operation;
    }

    private Documentation generic(HandlerMethod method) {
        String tag = tagFor(method.getBeanType().getSimpleName());
        String name = method.getMethod().getName().toLowerCase(Locale.ROOT);
        String summary = name.startsWith("get") || name.startsWith("list") || name.equals("mappings") || name.equals("connections")
                ? "Xem dữ liệu " + tag.toLowerCase(Locale.ROOT)
                : name.startsWith("create") || name.equals("submit") || name.equals("request")
                        ? "Tạo dữ liệu " + tag.toLowerCase(Locale.ROOT)
                        : name.startsWith("update") || name.startsWith("review") || name.startsWith("decide")
                                ? "Cập nhật dữ liệu " + tag.toLowerCase(Locale.ROOT)
                                : name.startsWith("delete") || name.startsWith("disconnect")
                                        ? "Xóa hoặc ngắt kết nối " + tag.toLowerCase(Locale.ROOT)
                                        : "Thực hiện tác vụ " + tag.toLowerCase(Locale.ROOT);
        return new Documentation(tag, summary,
                "Thực hiện chức năng " + tag.toLowerCase(Locale.ROOT) + " theo phạm vi quyền hiện có của người dùng.");
    }

    private String sessionAndCsrfNote(HandlerMethod method) {
        String name = method.getMethod().getName();
        if (method.getBeanType().getSimpleName().equals("WebhookController")
                || method.getBeanType().getSimpleName().equals("PrivacyPolicyController")
                || name.equals("login") || name.equals("callback")) {
            return "";
        }
        boolean mutation = name.startsWith("create") || name.startsWith("update") || name.startsWith("delete")
                || name.startsWith("disconnect") || name.startsWith("connect") || name.startsWith("submit")
                || name.startsWith("review") || name.startsWith("decide") || name.startsWith("request")
                || name.equals("sync") || name.equals("transition") || name.equals("assignee")
                || name.equals("sprint") || name.equals("estimate") || name.equals("start") || name.equals("close");
        return mutation
                ? " Yêu cầu đăng nhập bằng browser session. Mutation yêu cầu CSRF và Swagger UI tự gắn token."
                : " Yêu cầu đăng nhập bằng browser session.";
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
    private void documentSchemaProperties(Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }
        ((Map<String, Schema>) schema.getProperties()).forEach((name, property) -> {
            if (isBlank(property.getDescription())) {
                property.setDescription("Trường " + vietnameseSchemaName(name) + ".");
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
            case "SubjectController" -> "Môn học";
            case "MyCourseTeamController", "TeamRosterController" -> "Nhóm";
            case "TeamProjectController", "ProjectDetailController" -> "Dự án";
            case "ProjectIntegrationController", "ProjectIntegrationCallbackController" -> "Tích hợp dự án";
            case "ProjectGitHubReadController" -> "GitHub";
            case "ProjectTaskReadController" -> "Jira Task";
            case "ProjectSprintController" -> "Jira Sprint";
            case "PersonalIntegrationController" -> "Tích hợp cá nhân";
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map.Entry<String, Documentation> entry(String key, String summary, String description) {
        String controller = key.substring(0, key.indexOf('#'));
        return Map.entry(key, new Documentation(tagForStatic(controller), summary, description));
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
