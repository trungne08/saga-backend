package com.saga.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    private static final Map<String, String> TAG_CATALOG = tagCatalog();

    @Bean
    public OpenAPI customOpenAPI() {
        String securitySchemeName = "sessionCookie";

        return new OpenAPI()
                .info(new Info()
                        .title("SAGA Backend API")
                        .version("1.0.0")
                        .description("Tài liệu API SAGA. API nghiệp vụ yêu cầu đăng nhập bằng browser session; "
                                + "các thao tác thay đổi dữ liệu dùng CSRF do Swagger UI tự gắn."))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(
                        securitySchemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Browser session của người dùng đã đăng nhập")
                ))
                .tags(TAG_CATALOG.entrySet().stream()
                        .map(entry -> tag(entry.getKey(), entry.getValue()))
                        .toList());
    }

    /** Giữ đúng thứ tự workflow và loại mọi global tag không còn operation. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public OpenApiCustomizer usedTagOrderingCustomizer() {
        return openApi -> {
            Set<String> usedTags = new LinkedHashSet<>();
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    if (operation.getTags() != null) {
                        usedTags.addAll(operation.getTags());
                    }
                }));
            }
            List<Tag> orderedTags = TAG_CATALOG.entrySet().stream()
                    .filter(entry -> usedTags.contains(entry.getKey()))
                    .map(entry -> tag(entry.getKey(), entry.getValue()))
                    .toList();
            openApi.setTags(orderedTags);
        };
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static Map<String, String> tagCatalog() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Xác thực", "Đăng nhập, tài khoản hiện tại, browser session và CSRF.");
        tags.put("Quản trị", "API vận hành toàn cục dành cho quản trị viên.");
        tags.put("Học kỳ", "Quản lý dữ liệu học kỳ.");
        tags.put("Môn học", "Quản lý dữ liệu môn học.");
        tags.put("Lớp học", "Quản lý dữ liệu lớp học.");
        tags.put("Khóa học", "Quản lý khóa học, danh sách sinh viên và import.");
        tags.put("Nhóm", "Thông tin nhóm và thành viên nhóm.");
        tags.put("Dự án", "Thông tin, thống kê và thiết lập dự án.");
        tags.put("Jira Task", "Đọc và thay đổi Task có Jira làm source of truth.");
        tags.put("Jira Sprint", "Đọc và thay đổi Sprint có Jira làm source of truth.");
        tags.put("Tích hợp cá nhân", "Kết nối Jira và GitHub cho tài khoản đang đăng nhập.");
        tags.put("Tích hợp dự án", "Liên kết, trạng thái và đồng bộ Jira/GitHub của dự án.");
        tags.put("GitHub", "Đọc repository, nhánh và commit GitHub qua backend.");
        tags.put("Đồng bộ dữ liệu", "Ánh xạ danh tính và nhận kết quả callback đã lưu an toàn.");
        tags.put("Thông báo", "Notification Bell, phát thông báo và đăng ký trình duyệt nhận FCM.");
        tags.put("Đóng góp", "Cấu hình và đánh giá tỷ lệ đóng góp.");
        tags.put("Đánh giá", "Peer Review, rubric và phân tích học tập.");
        tags.put("Webhook", "Endpoint hệ thống dành cho webhook của nhà cung cấp; FE không gọi thủ công.");
        tags.put("Chính sách riêng tư", "Nội dung chính sách công khai.");
        return tags;
    }
}
