package com.urlshortener.controller;

import com.urlshortener.dto.ApiResponse;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    @PostMapping("/api/v1/shorten")
    public ResponseEntity<ApiResponse<ShortenResponse>> shorten(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : "anonymous";
        ShortenResponse response = urlService.shorten(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("URL shortened successfully", response));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer   = request.getHeader("Referer");

        String originalUrl = urlService.resolve(shortCode, ip, userAgent, referer);

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, originalUrl)
                .header("Cache-Control", "no-cache, no-store")
                .build();
    }

    @GetMapping("/api/v1/stats/{shortCode}")
    public ResponseEntity<ApiResponse<UrlStatsResponse>> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getStats(shortCode)));
    }

    @GetMapping("/api/v1/urls")
    public ResponseEntity<ApiResponse<List<ShortenResponse>>> listMyUrls(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(urlService.listUserUrls(userDetails.getUsername())));
    }

    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<ApiResponse<Void>> deleteUrl(
            @PathVariable String shortCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        urlService.delete(shortCode, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("URL deactivated", null));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}
