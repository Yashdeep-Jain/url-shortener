package com.urlshortener.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Base62 encoder for generating compact, URL-safe short codes.
 * Uses [a-z A-Z 0-9] — 62 chars — to maximise entropy in minimal characters.
 * A 7-char code provides 62^7 ≈ 3.5 trillion unique combinations.
 */
@Component
public class Base62Encoder {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = ALPHABET.length(); // 62
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Encode a numeric ID into a base-62 string.
     * Deterministic: same input always produces same output.
     */
    public String encode(long id) {
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decode a base-62 string back to a numeric ID.
     */
    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + ALPHABET.indexOf(c);
        }
        return result;
    }

    /**
     * Generate a random short code of the given length.
     * Used as a fallback when ID-based encoding produces collisions.
     */
    public String generateRandom(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(BASE)));
        }
        return sb.toString();
    }

    /**
     * Detect device type from User-Agent string.
     */
    public static String detectDevice(String userAgent) {
        if (userAgent == null) return "unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }
}
