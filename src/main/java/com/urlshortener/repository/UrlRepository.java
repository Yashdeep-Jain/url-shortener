package com.urlshortener.repository;

import com.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    Optional<Url> findByShortCodeAndActiveTrue(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<Url> findByUserIdOrderByCreatedAtDesc(String userId);

    // Atomic click counter increment (avoids optimistic lock overhead)
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);

    // Bulk deactivate expired URLs (scheduled cleanup)
    @Modifying
    @Query("UPDATE Url u SET u.active = false WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now AND u.active = true")
    int deactivateExpiredUrls(@Param("now") LocalDateTime now);

    // Top URLs by clicks for a user
    @Query("SELECT u FROM Url u WHERE u.userId = :userId AND u.active = true ORDER BY u.clickCount DESC")
    List<Url> findTopByUserIdOrderByClickCountDesc(@Param("userId") String userId);
}
