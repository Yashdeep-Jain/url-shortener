package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL too long")
    private String url;

    @Size(max = 255, message = "Title too long")
    private String title;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$",
             message = "Alias: 3-20 alphanumeric chars, hyphens or underscores only")
    private String customAlias;

    private LocalDateTime expiresAt;
}
