package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "click_events",
    indexes = {
        @Index(name = "idx_click_short_code", columnList = "shortCode"),
        @Index(name = "idx_click_created_at", columnList = "clickedAt"),
        @Index(name = "idx_click_country", columnList = "country")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String shortCode;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String referer;

    @Column(length = 50)
    private String country;

    @Column(length = 50)
    private String city;

    @Column(length = 20)
    private String deviceType; // mobile, desktop, tablet

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime clickedAt;
}
