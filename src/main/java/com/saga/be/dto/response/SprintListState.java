package com.saga.be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái dữ liệu danh sách Sprint của Team.")
public enum SprintListState {
    PROJECT_NOT_CREATED,
    EMPTY,
    READY
}
