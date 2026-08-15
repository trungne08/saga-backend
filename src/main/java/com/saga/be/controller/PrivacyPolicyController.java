package com.saga.be.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Chính sách riêng tư", description = "Nội dung chính sách công khai.")
public class PrivacyPolicyController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final String CONTACT_URL_PLACEHOLDER = "{{CONTACT_URL}}";

    private final String contactUrl;

    public PrivacyPolicyController(@Value("${app.privacy.contact-url:}") String contactUrl) {
        this.contactUrl = contactUrl;
    }

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPrivacyPolicy() {
        URI contactUri = validatedContactUri();
        String policy = loadPolicyTemplate().replace(
                CONTACT_URL_PLACEHOLDER,
                HtmlUtils.htmlEscape(contactUri.toString())
        );
        return ResponseEntity.ok().contentType(HTML_UTF8).body(policy);
    }

    private URI validatedContactUri() {
        try {
            URI uri = URI.create(contactUrl.trim());
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid public contact URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Privacy contact configuration is unavailable"
            );
        }
    }

    private String loadPolicyTemplate() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("static/privacy.html").getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Privacy policy is temporarily unavailable"
            );
        }
    }
}
