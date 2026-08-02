package com.saga.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.headers.Header;
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
                        .title("SAGA System API")
                        .version("1.0.0")
                        .description("SAGA backend API"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(
                        securitySchemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Authenticated Spring Security session")
                ))
                .path("/api/auth/logout", new PathItem().post(new Operation()
                        .addTagsItem("Authentication")
                        .summary("Log out the current browser session")
                        .description("Spring Security framework-managed logout. A valid "
                                + "X-XSRF-TOKEN CSRF header is required. With a valid CSRF token, "
                                + "the framework invalidates any current JSESSIONID session and "
                                + "redirects the browser to Cognito logout; this also applies when "
                                + "there is no current session.")
                        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                        .addParametersItem(new Parameter()
                                .in("header")
                                .name("X-XSRF-TOKEN")
                                .required(true)
                                .description("CSRF token value obtained from the XSRF-TOKEN cookie")
                                .schema(new StringSchema()))
                        .responses(new ApiResponses()
                                .addApiResponse("302", new ApiResponse()
                                        .description("Redirect to the Cognito logout endpoint")
                                        .headers(java.util.Map.of("Location", new Header()
                                                .description("Cognito logout URL")
                                                .schema(new StringSchema().format("uri")))))
                                .addApiResponse("403", new ApiResponse()
                                        .description("Missing or invalid X-XSRF-TOKEN CSRF header")))));
    }
}
