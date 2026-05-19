package com.aisay.manga.controller;

import com.aisay.manga.dto.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {

    /**
     * 作用：把 Spring Boot 默认错误入口统一转换成 ApiResponse。
     * 调用方：Spring MVC 在请求落入 /error 时自动调用。
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        int statusCode = resolveStatusCode(request);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(status.value(), resolveMessage(status)));
    }

    /**
     * 作用：从 Servlet 错误属性中解析 HTTP 状态码。
     * 调用方：handleError。
     */
    private int resolveStatusCode(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer statusCode) {
            return statusCode;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    /**
     * 作用：根据 HTTP 状态码生成统一错误提示文案。
     * 调用方：handleError。
     */
    private String resolveMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "接口不存在";
            case FORBIDDEN -> "无权访问该资源";
            case UNAUTHORIZED -> "用户未认证";
            case BAD_REQUEST -> "请求参数错误";
            default -> "服务器内部错误";
        };
    }
}
