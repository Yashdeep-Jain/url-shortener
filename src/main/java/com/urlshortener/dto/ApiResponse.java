package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response wrapper.
 * Written without @Builder because Lombok's @Builder does not work correctly
 * with generic type parameters on static factory methods.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String error;

    // Default constructor for Jackson deserialization
    public ApiResponse() {}

    private ApiResponse(boolean success, String message, T data, String error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error);
    }

    // ─── Getters (Jackson needs these) ───────────────────────────────────────

    public boolean isSuccess()  { return success; }
    public String getMessage()  { return message; }
    public T getData()          { return data; }
    public String getError()    { return error; }
}
