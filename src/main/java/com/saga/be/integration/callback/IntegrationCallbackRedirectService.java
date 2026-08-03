package com.saga.be.integration.callback;

import com.saga.be.config.IntegrationCallbackProperties;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class IntegrationCallbackRedirectService {

    private final IntegrationCallbackProperties properties;

    public IntegrationCallbackRedirectService(
            IntegrationCallbackProperties properties
    ) {
        this.properties = properties;
    }

    public URI callbackResultUri(String resultId) {
        return UriComponentsBuilder
                .fromUriString(properties.callbackRedirectUri())
                .replaceQuery(null)
                .queryParam("resultId", resultId)
                .build()
                .encode()
                .toUri();
    }
}
