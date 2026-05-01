package com.urlshortener.service;

import com.urlshortener.dto.ClickEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, ClickEventDto> kafkaTemplate;

    @Value("${app.kafka.topic.click-events:click-events}")
    private String clickEventsTopic;

    public void publishClickEvent(ClickEventDto event) {
        CompletableFuture<SendResult<String, ClickEventDto>> future =
                kafkaTemplate.send(clickEventsTopic, event.getShortCode(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Click event sent for {} to partition {}",
                        event.getShortCode(),
                        result.getRecordMetadata().partition());
            } else {
                log.warn("Failed to send click event for {}: {}",
                        event.getShortCode(), ex.getMessage());
            }
        });
    }
}
