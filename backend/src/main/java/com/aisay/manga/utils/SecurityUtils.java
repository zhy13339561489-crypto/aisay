package com.aisay.manga.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    /**
     * 作用：禁止实例化工具类。
     * 调用方：无，由 JVM 在误用反射构造时触发。
     */
    private SecurityUtils() {
    }

    /**
     * 作用：从 Spring Security 上下文中读取当前登录用户 ID。
     * 调用方：各 Controller 在调用 Service 前获取当前用户身份。
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("当前用户未认证");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String value) {
            return Long.valueOf(value);
        }

        throw new IllegalStateException("无法解析当前用户ID");
    }
}
