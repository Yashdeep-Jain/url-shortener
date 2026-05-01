package com.urlshortener.exception;
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() { super("Rate limit exceeded. Please slow down."); }
}
