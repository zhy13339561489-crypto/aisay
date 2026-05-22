package com.aisay.manga.config;

import java.util.Set;

public final class UserRoles {

    public static final String ROOT = "ROOT";

    public static final String ADMIN = "ADMIN";

    public static final String USER = "USER";

    private static final Set<String> ALLOWED_ROLES = Set.of(ROOT, ADMIN, USER);

    private UserRoles() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return USER;
        }
        String normalized = role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("用户权限等级只能是 ROOT、ADMIN 或 USER");
        }
        return normalized;
    }

    public static boolean isRoot(String role) {
        return ROOT.equals(normalize(role));
    }

    public static boolean isAdminOrRoot(String role) {
        String normalized = normalize(role);
        return ROOT.equals(normalized) || ADMIN.equals(normalized);
    }
}
