package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

// ─── Request DTOs ─────────────────────────────────────────────────────────────

public class Dtos {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ShortenRequest {
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL too long")
        private String url;

        @Size(max = 50, message = "Title too long")
        private String title;

        // Optional custom alias (e.g. "my-product")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$",
                 message = "Alias: 3-20 alphanumeric chars, hyphens or underscores only")
        private String customAlias;

        // null = never expires
        private LocalDateTime expiresAt;
    }

    // ─── Response DTOs ────────────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShortenResponse {
        private String shortUrl;
        private String shortCode;
        private String originalUrl;
        private String title;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UrlStatsResponse {
        private String shortCode;
        private String originalUrl;
        private Long totalClicks;
        private LocalDateTime createdAt;
        private LocalDateTime lastClickedAt;
        private Map<String, Long> clicksByCountry;
        private Map<String, Long> clicksByDevice;
        private Map<String, Long> clicksByDay;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private String error;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true).data(data).build();
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true).message(message).data(data).build();
        }

        public static <T> ApiResponse<T> error(String error) {
            return ApiResponse.<T>builder()
                    .success(false).error(error).build();
        }
    }

    // ─── Kafka Event ──────────────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ClickEventDto {
        private String shortCode;
        private String ipAddress;
        private String userAgent;
        private String referer;
        private LocalDateTime clickedAt;
    }
}
