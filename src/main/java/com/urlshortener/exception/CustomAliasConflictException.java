package com.urlshortener.exception;
public class CustomAliasConflictException extends RuntimeException {
    public CustomAliasConflictException(String msg) { super(msg); }
}
