package com.urlshortener.exception;
public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String code) { super("URL has expired: " + code); }
}
