package com.aisay.manga.controller;

import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.service.StoryService;
import com.aisay.manga.utils.SecurityUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/story")
public class StoryController {

    private final StoryService storyService;

    /**
     * 作用：注入故事服务。
     * 调用方：Spring 容器启动时自动构造 StoryController。
     */
    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    /**
     * 作用：根据题材和可选剧情描述生成漫剧剧情大纲，并新建漫剧记录。
     * 调用方：前端“生成剧情大纲/生成漫剧”按钮请求 POST /api/story/generate。
     */
    @PostMapping("/generate")
    public ApiResponse<StoryResponse> generateStory(@Valid @RequestBody StoryGenerateRequest request) {
        return ApiResponse.success("Story generated", storyService.generateStory(SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：根据用户修改意见调用 Python 大纲修改接口，更新剧情大纲和角色设定。
     * 调用方：前端故事详情页“大纲修改”按钮请求 POST /api/story/{id}/outline/revise。
     */
    @PostMapping("/{id}/outline/revise")
    public ApiResponse<StoryDetailResponse> reviseStoryOutline(
            @PathVariable Long id,
            @Valid @RequestBody StoryOutlineReviseRequest request
    ) {
        return ApiResponse.success("Outline revised", storyService.reviseStoryOutline(id, SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：手动保存故事摘要、完整大纲和角色设定。
     * 调用方：前端故事详情页手动编辑剧情大纲或角色设定后请求 PUT /api/story/{id}/detail。
     */
    @PutMapping("/{id}/detail")
    public ApiResponse<StoryDetailResponse> updateStoryDetail(
            @PathVariable Long id,
            @Valid @RequestBody StoryDetailUpdateRequest request
    ) {
        return ApiResponse.success("Story detail updated", storyService.updateStoryDetail(id, SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：基于现有剧情大纲和角色设定生成分卷大纲，并保存到分卷大纲表。
     * 调用方：前端故事详情页“分卷大纲生成”按钮请求 POST /api/story/{id}/volume-outline/generate。
     */
    @PostMapping("/{id}/volume-outline/generate")
    public ApiResponse<StoryDetailResponse> generateVolumeOutline(@PathVariable Long id) {
        return ApiResponse.success("Volume outline generated", storyService.generateVolumeOutline(id, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：查询单个漫剧的详情，包括剧情大纲、角色设定和分卷大纲。
     * 调用方：前端故事详情页、聊天页绑定故事信息刷新时请求 GET /api/story/{id}。
     */
    @GetMapping("/{id}")
    public ApiResponse<StoryDetailResponse> getStoryDetail(@PathVariable Long id) {
        StoryDetailResponse storyDetailResponse = storyService.getStoryDetail(id, SecurityUtils.getCurrentUserId());
        return ApiResponse.success(storyDetailResponse);
    }

    /**
     * 作用：分页查询当前用户的漫剧列表。
     * 调用方：前端漫剧列表页、创建对话时的漫剧选择列表请求 GET /api/story/list。
     */
    @GetMapping("/list")
    public ApiResponse<Page<StoryResponse>> getUserStories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(storyService.getUserStories(SecurityUtils.getCurrentUserId(), page, size));
    }

    /**
     * 作用：更新漫剧基础信息，如标题、题材、风格和摘要。
     * 调用方：前端漫剧基础信息编辑入口请求 PUT /api/story/{id}。
     */
    @PutMapping("/{id}")
    public ApiResponse<StoryResponse> updateStory(
            @PathVariable Long id,
            @Valid @RequestBody StoryUpdateRequest request
    ) {
        return ApiResponse.success("Story updated", storyService.updateStory(id, SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：删除指定漫剧。
     * 调用方：前端漫剧列表或详情页删除按钮请求 DELETE /api/story/{id}。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteStory(@PathVariable Long id) {
        storyService.deleteStory(id, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("Story deleted", null);
    }
}
