package com.saga.be.dto.response;

import java.util.List;

public record TraceabilityLinksResponse<T>(List<T> items, boolean truncated) {

    public TraceabilityLinksResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
