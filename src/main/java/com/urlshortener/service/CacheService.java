package com.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Centralized Redis cache service.
 *
 * Key strategy:
 *   url:{shortCode}      → original URL (most read)
 *   url:exists:{code}    → boolean bloom-filter substitute (negative cache)
 *   stats:{shortCode}    → serialized stats object
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.cache.url-ttl-seconds:86400}")
    private long urlTtlSeconds;

    @Value("${app.cache.stats-ttl-seconds:300}")
    private long statsTtlSeconds;

    private static final String URL_KEY_PREFIX    = "url:";
    private static final String EXISTS_KEY_PREFIX = "url:exists:";
    private static final String STATS_KEY_PREFIX  = "stats:";
    private static final String NOT_FOUND_VALUE   = "__NOT_FOUND__";

    // ─── URL cache ────────────────────────────────────────────────────────────

    public void cacheUrl(String shortCode, String originalUrl) {
        try {
            redisTemplate.opsForValue()
                    .set(URL_KEY_PREFIX + shortCode, originalUrl,
                         Duration.ofSeconds(urlTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", shortCode, e.getMessage());
        }
    }

    public Optional<String> getCachedUrl(String shortCode) {
        try {
            String value = redisTemplate.opsForValue().get(URL_KEY_PREFIX + shortCode);
            if (value == null) return Optional.empty();
            if (NOT_FOUND_VALUE.equals(value)) return Optional.of(NOT_FOUND_VALUE);
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Negative caching — cache the fact that a short code does NOT exist.
     * Prevents DB hits for invalid/non-existent codes (common attack vector).
     */
    public void cacheNotFound(String shortCode) {
        try {
            redisTemplate.opsForValue()
                    .set(URL_KEY_PREFIX + shortCode, NOT_FOUND_VALUE,
                         Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Redis negative cache failed: {}", e.getMessage());
        }
    }

    public boolean isNotFoundCached(String value) {
        return NOT_FOUND_VALUE.equals(value);
    }

    public void evictUrl(String shortCode) {
        try {
            redisTemplate.delete(URL_KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis evict failed for {}: {}", shortCode, e.getMessage());
        }
    }

    // ─── Stats cache ──────────────────────────────────────────────────────────

    public void cacheStats(String shortCode, String statsJson) {
        try {
            redisTemplate.opsForValue()
                    .set(STATS_KEY_PREFIX + shortCode, statsJson,
                         Duration.ofSeconds(statsTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis stats write failed: {}", e.getMessage());
        }
    }

    public Optional<String> getCachedStats(String shortCode) {
        try {
            return Optional.ofNullable(
                    redisTemplate.opsForValue().get(STATS_KEY_PREFIX + shortCode));
        } catch (Exception e) {
            log.warn("Redis stats read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void evictStats(String shortCode) {
        try {
            redisTemplate.delete(STATS_KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis stats evict failed: {}", e.getMessage());
        }
    }

    // ─── Rate limiting (sliding window) ──────────────────────────────────────

    /**
     * Increment a rate-limit counter for a given key (IP or user).
     * Returns the current count after increment.
     */
    public long incrementRateLimit(String ip, int windowSeconds) {
        String key = "rl:" + ip;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Rate limit Redis error: {}", e.getMessage());
            return 0; // fail open — don't block requests on Redis failure
        }
    }
}
