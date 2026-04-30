package com.yef;

public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "success",null);
    }


    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1, message, null);
    }
}
