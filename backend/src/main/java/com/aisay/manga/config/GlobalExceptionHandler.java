package com.aisay.manga.config;

import com.aisay.manga.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 作用：处理 @Valid 请求体校验失败，并返回字段级错误。
     * 调用方：Spring MVC 异常处理机制自动调用。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "参数校验失败", errors, LocalDateTime.now()));
    }

    /**
     * 作用：处理参数约束校验失败，并返回约束路径和错误消息。
     * 调用方：Spring MVC 异常处理机制自动调用。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "参数校验失败", errors, LocalDateTime.now()));
    }

    /**
     * 作用：处理请求参数、文件上传等 400 类异常。
     * 调用方：Spring MVC 异常处理机制自动调用。
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, resolveMessage(ex, "请求参数错误")));
    }

    /**
     * 作用：处理无权限访问异常。
     * 调用方：Spring Security / Spring MVC 异常处理机制自动调用。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(403, "无权访问该资源"));
    }

    /**
     * 作用：处理资源不存在异常。
     * 调用方：Service 层抛出 NoSuchElementException 后由 Spring MVC 自动调用。
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(404, resolveMessage(ex, "资源不存在")));
    }

    /**
     * 作用：处理接口或静态资源不存在异常。
     * 调用方：Spring MVC 未找到处理器或资源时自动调用。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(404, "接口不存在"));
    }

    /**
     * 作用：处理文件读写相关异常。
     * 调用方：LocalFileStorageUtil 抛出 UncheckedIOException 后由 Spring MVC 自动调用。
     */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileIo(UncheckedIOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(500, resolveMessage(ex, "文件处理失败")));
    }

    /**
     * 作用：兜底处理未被前面规则捕获的后端异常。
     * 调用方：Spring MVC 异常处理机制自动调用。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(500, "服务器内部错误"));
    }

    /**
     * 作用：优先使用异常消息，没有消息时使用默认提示。
     * 调用方：handleBadRequest、handleNotFound、handleFileIo。
     */
    private String resolveMessage(Exception ex, String fallback) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }
}
