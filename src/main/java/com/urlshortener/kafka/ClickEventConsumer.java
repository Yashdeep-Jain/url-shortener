package com.urlshortener.kafka;

import com.urlshortener.dto.ClickEventDto;
import com.urlshortener.model.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClickEventConsumer {

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;

    @KafkaListener(
            topics = "${app.kafka.topic.click-events:click-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeClickEvent(
            List<ClickEventDto> events,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets) {

        if (events == null || events.isEmpty()) return;

        log.debug("Consuming batch of {} click events", events.size());

        List<ClickEvent> clickEntities = new ArrayList<>(events.size());

        for (ClickEventDto event : events) {
            try {
                ClickEvent entity = ClickEvent.builder()
                        .shortCode(event.getShortCode())
                        .ipAddress(anonymizeIp(event.getIpAddress()))
                        .userAgent(event.getUserAgent())
                        .referer(event.getReferer())
                        .deviceType(Base62Encoder.detectDevice(event.getUserAgent()))
                        .clickedAt(event.getClickedAt())
                        .build();
                clickEntities.add(entity);
            } catch (Exception e) {
                log.error("Failed to map click event for {}: {}", event.getShortCode(), e.getMessage());
            }
        }

        clickEventRepository.saveAll(clickEntities);

        events.stream()
                .map(ClickEventDto::getShortCode)
                .distinct()
                .forEach(code -> {
                    long codeCount = events.stream()
                            .filter(e -> code.equals(e.getShortCode()))
                            .count();
                    for (int i = 0; i < codeCount; i++) {
                        urlRepository.incrementClickCount(code);
                    }
                });

        log.debug("Persisted {} click events", clickEntities.size());
    }

    private String anonymizeIp(String ip) {
        if (ip == null) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) return ip.substring(0, lastDot) + ".0";
        return ip;
    }
}
