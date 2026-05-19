package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private Integer code;

    private String message;

    private T data;

    private LocalDateTime timestamp;

    /**
     * 作用：构造默认 success 消息的成功响应。
     * 调用方：各 Controller 返回成功结果时调用。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, LocalDateTime.now());
    }

    /**
     * 作用：构造自定义成功消息的成功响应。
     * 调用方：各 Controller 需要指定成功提示时调用。
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, LocalDateTime.now());
    }

    /**
     * 作用：构造统一失败响应。
     * 调用方：GlobalExceptionHandler、ApiErrorController、SecurityConfig 未认证处理器。
     */
    public static <T> ApiResponse<T> fail(Integer code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }
}
