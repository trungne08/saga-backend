package com.saga.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

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
                .tags(List.of(
                        tag("Xác thực", "Đăng nhập, phiên làm việc và CSRF."),
                        tag("Quản trị", "Các màn hình đọc dữ liệu vận hành toàn cục dành riêng cho quản trị viên."),
                        tag("Học kỳ", "Quản lý dữ liệu học kỳ."),
                        tag("Môn học", "Quản lý dữ liệu môn học."),
                        tag("Lớp học", "Quản lý dữ liệu lớp học."),
                        tag("Khóa học", "Quản lý khóa học và thành viên khóa học."),
                        tag("Giảng viên", "Dữ liệu và phân tích dành cho giảng viên."),
                        tag("Sinh viên", "Dữ liệu sinh viên trong phạm vi được cấp quyền."),
                        tag("Nhóm", "Thông tin nhóm và thành viên nhóm."),
                        tag("Dự án", "Thông tin, thống kê và liên kết dự án."),
                        tag("Tích hợp dự án", "Liên kết, trạng thái và đồng bộ Jira/GitHub của dự án."),
                        tag("Tích hợp cá nhân", "Kết nối Jira/GitHub theo tài khoản hiện tại."),
                        tag("Jira Task", "Đọc và thay đổi task đồng bộ với Jira."),
                        tag("Jira Sprint", "Đọc và thay đổi Sprint đồng bộ với Jira."),
                        tag("GitHub", "Đọc repository, nhánh và commit GitHub qua backend."),
                        tag("Đồng bộ dữ liệu", "Điều phối, lịch sử và xử lý ánh xạ đồng bộ."),
                        tag("Đóng góp", "Đánh giá và điều chỉnh đóng góp."),
                        tag("Đánh giá", "Peer review và phân tích học tập."),
                        tag("Webhook", "Endpoint dành riêng cho webhook của nhà cung cấp."),
                        tag("Chính sách riêng tư", "Nội dung chính sách công khai.")
                ));
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
