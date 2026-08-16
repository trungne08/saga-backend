package com.saga.be.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Sparse browser-session update for the current Student or Lecturer profile. */
@Schema(description = "Chi cho phep cap nhat ten hien thi va avatar cua chinh nguoi dung dang nhap.")
public final class SelfProfileUpdateRequest {

    @Size(max = 255)
    private String fullName;

    @Size(max = 2048)
    private String avatarUrl;

    private boolean fullNamePresent;
    private boolean avatarUrlPresent;

    public String getFullName() {
        return fullName;
    }

    @JsonSetter("fullName")
    public void setFullName(String fullName) {
        this.fullNamePresent = true;
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    @JsonSetter("avatarUrl")
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrlPresent = true;
        this.avatarUrl = avatarUrl;
    }

    @JsonIgnore
    public boolean isFullNamePresent() {
        return fullNamePresent;
    }

    @JsonIgnore
    public boolean isAvatarUrlPresent() {
        return avatarUrlPresent;
    }
}
