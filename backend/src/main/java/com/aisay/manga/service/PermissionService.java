package com.aisay.manga.service;

public interface PermissionService {

    /**
     * 作用：要求当前用户为 root，否则抛出无权限异常。
     * 调用方：用户权限管理接口。
     */
    void requireRoot(Long userId);

    /**
     * 作用：要求当前用户为 root 或 admin，否则抛出无权限异常。
     * 调用方：大纲配置管理和 Prompt 管理接口。
     */
    void requireAdminOrRoot(Long userId);
}
