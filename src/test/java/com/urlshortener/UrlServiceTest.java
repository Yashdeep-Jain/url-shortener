package com.urlshortener;

import com.urlshortener.dto.ClickEventDto;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.CustomAliasConflictException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.CacheService;
import com.urlshortener.service.KafkaProducerService;
import com.urlshortener.service.UrlService;
import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock UrlRepository urlRepository;
    @Mock CacheService cacheService;
    @Mock KafkaProducerService kafkaProducerService;
    @Mock Base62Encoder base62Encoder;

    @InjectMocks UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(urlService, "shortCodeLength", 7);
    }

    @Test
    @DisplayName("Shorten valid URL returns short code")
    void shorten_ValidUrl_ReturnsShortCode() {
        ShortenRequest req = ShortenRequest.builder().url("https://example.com/long/path").build();
        when(base62Encoder.generateRandom(7)).thenReturn("abc1234");
        when(urlRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(urlRepository.save(any())).thenAnswer(inv -> {
            Url u = inv.getArgument(0);
            return u;
        });

        ShortenResponse response = urlService.shorten(req, "user1");

        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(response.getShortCode()).isEqualTo("abc1234");
        verify(cacheService).cacheUrl("abc1234", "https://example.com/long/path");
    }

    @Test
    @DisplayName("Custom alias conflict throws exception")
    void shorten_CustomAliasConflict_ThrowsException() {
        ShortenRequest req = ShortenRequest.builder().url("https://example.com").customAlias("taken").build();
        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.shorten(req, "user1"))
                .isInstanceOf(CustomAliasConflictException.class)
                .hasMessageContaining("taken");
    }

    @Test
    @DisplayName("Resolve returns cached URL without DB hit")
    void resolve_CacheHit_NoDatabaseCall() {
        when(cacheService.getCachedUrl("abc1234")).thenReturn(Optional.of("https://example.com"));
        when(cacheService.isNotFoundCached("https://example.com")).thenReturn(false);

        String result = urlService.resolve("abc1234", "1.2.3.0", "Mozilla/5.0", null);

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(urlRepository);
    }

    @Test
    @DisplayName("Resolve cache miss fetches from DB and back-fills cache")
    void resolve_CacheMiss_FetchesDbAndCaches() {
        Url url = Url.builder()
                .shortCode("xyz5678")
                .originalUrl("https://target.com")
                .active(true)
                .clickCount(0L)
                .build();

        when(cacheService.getCachedUrl("xyz5678")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCodeAndActiveTrue("xyz5678")).thenReturn(Optional.of(url));

        String result = urlService.resolve("xyz5678", "1.2.3.0", "Mozilla", null);

        assertThat(result).isEqualTo("https://target.com");
        verify(cacheService).cacheUrl("xyz5678", "https://target.com");
    }

    @Test
    @DisplayName("Resolve non-existent code caches not-found and throws")
    void resolve_NotFound_CachesNegativeAndThrows() {
        when(cacheService.getCachedUrl("ghost99")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCodeAndActiveTrue("ghost99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolve("ghost99", "1.2.3.0", null, null))
                .isInstanceOf(ShortUrlNotFoundException.class);
        verify(cacheService).cacheNotFound("ghost99");
    }

    @Test
    @DisplayName("Invalid URL throws IllegalArgumentException")
    void shorten_InvalidUrl_ThrowsException() {
        ShortenRequest req = ShortenRequest.builder().url("not-a-valid-url").build();

        assertThatThrownBy(() -> urlService.shorten(req, "user1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
