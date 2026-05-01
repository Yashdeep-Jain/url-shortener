package com.urlshortener.repository;

import com.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT MAX(c.clickedAt) FROM ClickEvent c WHERE c.shortCode = :shortCode")
    LocalDateTime findLastClickTime(@Param("shortCode") String shortCode);

    // Clicks by country
    @Query("SELECT c.country, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<Object[]> countByShortCodeGroupByCountry(@Param("shortCode") String shortCode);

    // Clicks by device type
    @Query("SELECT c.deviceType, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.deviceType")
    List<Object[]> countByShortCodeGroupByDevice(@Param("shortCode") String shortCode);

    // Daily clicks (last 30 days)
    @Query(value = "SELECT DATE(clicked_at) as day, COUNT(*) as clicks " +
                   "FROM click_events WHERE short_code = :shortCode " +
                   "AND clicked_at >= :since GROUP BY DATE(clicked_at) ORDER BY day",
           nativeQuery = true)
    List<Object[]> getDailyClicks(@Param("shortCode") String shortCode,
                                   @Param("since") LocalDateTime since);

    // Total clicks in date range
    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode " +
           "AND c.clickedAt BETWEEN :from AND :to")
    long countClicksInRange(@Param("shortCode") String shortCode,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);
}
