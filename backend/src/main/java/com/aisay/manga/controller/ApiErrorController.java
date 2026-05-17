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

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        int statusCode = resolveStatusCode(request);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(status.value(), resolveMessage(status)));
    }

    private int resolveStatusCode(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer statusCode) {
            return statusCode;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

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
