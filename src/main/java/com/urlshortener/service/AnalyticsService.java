package com.urlshortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        var cached = cacheService.getCachedStats(shortCode);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), UrlStatsResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached stats: {}", e.getMessage());
            }
        }

        var url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        long totalClicks = clickEventRepository.countByShortCode(shortCode);
        LocalDateTime lastClick = clickEventRepository.findLastClickTime(shortCode);

        Map<String, Long> byCountry = toMap(clickEventRepository.countByShortCodeGroupByCountry(shortCode));
        Map<String, Long> byDevice  = toMap(clickEventRepository.countByShortCodeGroupByDevice(shortCode));
        Map<String, Long> byDay     = toDayMap(clickEventRepository.getDailyClicks(shortCode, LocalDateTime.now().minusDays(30)));

        UrlStatsResponse stats = UrlStatsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(url.getOriginalUrl())
                .totalClicks(totalClicks)
                .createdAt(url.getCreatedAt())
                .lastClickedAt(lastClick)
                .clicksByCountry(byCountry)
                .clicksByDevice(byDevice)
                .clicksByDay(byDay)
                .build();

        try {
            cacheService.cacheStats(shortCode, objectMapper.writeValueAsString(stats));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize stats for cache: {}", e.getMessage());
        }

        return stats;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        return rows.stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue(),
                        Long::sum,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Long> toDayMap(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue(),
                        Long::sum,
                        LinkedHashMap::new
                ));
    }
}
