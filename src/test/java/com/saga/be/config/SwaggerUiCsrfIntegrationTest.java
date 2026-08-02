package com.saga.be.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SwaggerUiCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerInitializerAddsDecodedCsrfOnlyForUnsafeSameOriginMethods() throws Exception {
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"withCredentials\" : true")))
                .andExpect(content().string(containsString(
                        "const unsafeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);"
                )))
                .andExpect(content().string(containsString("requestInterceptor: async (request) =>")))
                .andExpect(content().string(containsString("fetch('/api/auth/csrf', {")))
                .andExpect(content().string(containsString("credentials: 'include'")))
                .andExpect(content().string(containsString("decodeURIComponent(rawValue)")))
                .andExpect(content().string(containsString("request.headers['X-XSRF-TOKEN']")))
                .andExpect(content().string(containsString(
                        "if (!unsafeMethods.has((request.method || 'GET').toUpperCase())) return request;"
                )));
    }
}
