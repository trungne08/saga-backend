package com.saga.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("SAGA System API")
                        .version("1.0.0")
                        .description("Tài liệu API cho Hệ thống Đánh giá Slicing Pie và Trợ lý Cảnh báo Sớm AI (SAGA)"))
                // Yêu cầu xác thực bảo mật cho toàn bộ API
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // Cấu hình lược đồ bảo mật (JWT Bearer Token từ Cognito)
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("Nhập JWT Token từ AWS Cognito vào đây")
                                )
                );
    }
}