package com.urlshortener.service;

import com.urlshortener.dto.ClickEventDto;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.CustomAliasConflictException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final CacheService cacheService;
    private final KafkaProducerService kafkaProducerService;
    private final Base62Encoder base62Encoder;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code-length:7}")
    private int shortCodeLength;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request, String userId) {
        validateUrl(request.getUrl());

        String shortCode;
        if (StringUtils.hasText(request.getCustomAlias())) {
            shortCode = request.getCustomAlias().toLowerCase();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new CustomAliasConflictException("Alias '" + shortCode + "' is already taken.");
            }
        } else {
            shortCode = generateUniqueCode();
        }

        Url url = Url.builder()
                .shortCode(shortCode)
                .originalUrl(request.getUrl())
                .title(request.getTitle())
                .userId(userId)
                .clickCount(0L)
                .active(true)
                .customAlias(StringUtils.hasText(request.getCustomAlias()))
                .expiresAt(request.getExpiresAt())
                .build();

        url = urlRepository.save(url);
        cacheService.cacheUrl(shortCode, url.getOriginalUrl());
        log.info("Created short URL: {} -> {}", shortCode, request.getUrl());

        return ShortenResponse.builder()
                .shortUrl(baseUrl + "/" + shortCode)
                .shortCode(shortCode)
                .originalUrl(url.getOriginalUrl())
                .title(url.getTitle())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode, String ip, String userAgent, String referer) {
        var cached = cacheService.getCachedUrl(shortCode);
        if (cached.isPresent()) {
            if (cacheService.isNotFoundCached(cached.get())) {
                throw new ShortUrlNotFoundException(shortCode);
            }
            publishClickEvent(shortCode, ip, userAgent, referer);
            return cached.get();
        }

        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> {
                    cacheService.cacheNotFound(shortCode);
                    return new ShortUrlNotFoundException(shortCode);
                });

        if (url.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }

        cacheService.cacheUrl(shortCode, url.getOriginalUrl());
        publishClickEvent(shortCode, ip, userAgent, referer);
        return url.getOriginalUrl();
    }

    private void publishClickEvent(String shortCode, String ip, String userAgent, String referer) {
        kafkaProducerService.publishClickEvent(
                ClickEventDto.builder()
                        .shortCode(shortCode)
                        .ipAddress(ip)
                        .userAgent(userAgent)
                        .referer(referer)
                        .clickedAt(LocalDateTime.now())
                        .build()
        );
    }

    @Transactional
    public void delete(String shortCode, String userId) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        if (!url.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this URL");
        }

        url.setActive(false);
        urlRepository.save(url);
        cacheService.evictUrl(shortCode);
        cacheService.evictStats(shortCode);
        log.info("Deactivated URL: {}", shortCode);
    }

    @Transactional(readOnly = true)
    public List<ShortenResponse> listUserUrls(String userId) {
        return urlRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(url -> ShortenResponse.builder()
                        .shortUrl(baseUrl + "/" + url.getShortCode())
                        .shortCode(url.getShortCode())
                        .originalUrl(url.getOriginalUrl())
                        .title(url.getTitle())
                        .expiresAt(url.getExpiresAt())
                        .createdAt(url.getCreatedAt())
                        .build())
                .toList();
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deactivateExpiredUrls() {
        int count = urlRepository.deactivateExpiredUrls(LocalDateTime.now());
        if (count > 0) log.info("Deactivated {} expired URLs", count);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = base62Encoder.generateRandom(shortCodeLength);
            if (!urlRepository.existsByShortCode(code)) return code;
        }
        return base62Encoder.encode(System.currentTimeMillis());
    }

    private void validateUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            String protocol = url.getProtocol();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + urlStr);
        }
    }
}
