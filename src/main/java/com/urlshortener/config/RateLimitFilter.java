package com.urlshortener.config;

import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.CacheService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * IP-based rate limiting using Redis sliding window counter.
 * Applied only to the shorten endpoint (POST /api/v1/shorten).
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements Filter {

    private final CacheService cacheService;

    @Value("${app.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Only rate-limit the shorten endpoint
        if ("POST".equals(request.getMethod()) &&
                request.getRequestURI().contains("/api/v1/shorten")) {

            String ip = getClientIp(request);
            long count = cacheService.incrementRateLimit(ip, 60);

            response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(Math.max(0, requestsPerMinute - count)));

            if (count > requestsPerMinute) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false,\"error\":\"Rate limit exceeded. Max "
                        + requestsPerMinute + " requests/minute.\"}");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
