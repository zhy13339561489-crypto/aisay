package com.aisay.manga.controller;

import com.aisay.manga.config.FeaturePermissionKeys;
import com.aisay.manga.dto.request.StoryOutlineOptionRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.StoryOutlineOptionResponse;
import com.aisay.manga.service.PermissionService;
import com.aisay.manga.service.StoryOutlineOptionService;
import com.aisay.manga.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/story-outline-options")
@RequiredArgsConstructor
public class StoryOutlineOptionController {

    private final StoryOutlineOptionService storyOutlineOptionService;

    private final PermissionService permissionService;

    /**
     * 作用：查询生成剧情大纲可选的题材和漫剧风格。
     * 调用方：前端生成剧情大纲弹窗、前端大纲配置管理页。
     */
    @GetMapping
    public ApiResponse<List<StoryOutlineOptionResponse>> listOptions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ApiResponse.success(storyOutlineOptionService.listOptions(type, enabled));
    }

    /**
     * 作用：新增题材或漫剧风格配置。
     * 调用方：前端大纲配置管理页。
     */
    @PostMapping
    public ApiResponse<StoryOutlineOptionResponse> createOption(@Valid @RequestBody StoryOutlineOptionRequest request) {
        permissionService.requireFeature(SecurityUtils.getCurrentUserId(), FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        return ApiResponse.success("配置项已创建", storyOutlineOptionService.createOption(request));
    }

    /**
     * 作用：修改题材或漫剧风格配置。
     * 调用方：前端大纲配置管理页。
     */
    @PutMapping("/{id}")
    public ApiResponse<StoryOutlineOptionResponse> updateOption(
            @PathVariable Long id,
            @Valid @RequestBody StoryOutlineOptionRequest request
    ) {
        permissionService.requireFeature(SecurityUtils.getCurrentUserId(), FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        return ApiResponse.success("配置项已更新", storyOutlineOptionService.updateOption(id, request));
    }

    /**
     * 作用：删除题材或漫剧风格配置。
     * 调用方：前端大纲配置管理页。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOption(@PathVariable Long id) {
        permissionService.requireFeature(SecurityUtils.getCurrentUserId(), FeaturePermissionKeys.OUTLINE_CONFIG_MANAGE);
        storyOutlineOptionService.deleteOption(id);
        return ApiResponse.success("配置项已删除", null);
    }
}
