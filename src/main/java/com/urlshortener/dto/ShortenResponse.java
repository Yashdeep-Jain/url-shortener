package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShortenResponse {
    private String shortUrl;
    private String shortCode;
    private String originalUrl;
    private String title;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
