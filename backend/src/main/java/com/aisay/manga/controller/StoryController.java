package com.aisay.manga.controller;

import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.request.StoryVolumeOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryVolumeOutlineUpdateRequest;
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
import org.springframework.web.multipart.MultipartFile;

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
     * 作用：根据题材、用户设定漫剧风格和可选剧情描述生成漫剧剧情大纲，并新建漫剧记录。
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
     * 作用：根据用户输入的修改意见调用 Python 自动修改分卷大纲，并将结果保存回分卷大纲表。
     * 调用方：前端故事详情页“自动修改分卷大纲”按钮请求 POST /api/story/{id}/volume-outline/revise。
     */
    @PostMapping("/{id}/volume-outline/revise")
    public ApiResponse<StoryDetailResponse> reviseVolumeOutline(
            @PathVariable Long id,
            @Valid @RequestBody StoryVolumeOutlineReviseRequest request
    ) {
        return ApiResponse.success("Volume outline revised", storyService.reviseVolumeOutline(id, SecurityUtils.getCurrentUserId(), request));
    }

    /**
     * 作用：根据某一卷的分卷大纲异步生成该卷的小节故事细节。
     * 调用方：前端故事详情页每个分卷卡片中的“生成小节故事”按钮。
     */
    @PostMapping("/{id}/volume-outline/{volumeId}/sections/generate")
    public ApiResponse<StoryDetailResponse> generateVolumeSections(
            @PathVariable Long id,
            @PathVariable Long volumeId
    ) {
        return ApiResponse.success("Volume section generation submitted", storyService.generateVolumeSections(id, volumeId, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：根据某一小节的故事细节异步生成或复用本节人物/场景图片资产。
     * 调用方：前端故事详情页每个小节卡片中的“生成人物/场景图片”按钮。
     */
    @PostMapping("/{id}/volume-sections/{sectionId}/assets/generate")
    public ApiResponse<StoryDetailResponse> generateSectionAssets(
            @PathVariable Long id,
            @PathVariable Long sectionId
    ) {
        return ApiResponse.success("Section asset generation submitted", storyService.generateSectionAssets(id, sectionId, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：根据某一小节的故事细节异步生成分镜故事脚本。
     * 调用方：前端故事详情页每个小节卡片中的“生成故事脚本”按钮。
     */
    @PostMapping("/{id}/volume-sections/{sectionId}/script/generate")
    public ApiResponse<StoryDetailResponse> generateSectionScript(
            @PathVariable Long id,
            @PathVariable Long sectionId
    ) {
        return ApiResponse.success("Section script generation submitted", storyService.generateSectionScript(id, sectionId, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 作用：保存用户手动编辑后的分卷大纲列表。
     * 调用方：前端故事详情页“手动修改分卷大纲”弹窗请求 PUT /api/story/{id}/volume-outline。
     */
    @PutMapping("/{id}/volume-outline")
    public ApiResponse<StoryDetailResponse> updateVolumeOutlines(
            @PathVariable Long id,
            @Valid @RequestBody StoryVolumeOutlineUpdateRequest request
    ) {
        return ApiResponse.success("Volume outline updated", storyService.updateVolumeOutlines(id, SecurityUtils.getCurrentUserId(), request));
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
     * 作用：为人物资产上传用户录制或准备好的音频。
     * 调用方：前端故事详情页小节资产卡片中的“上传人物音频”入口。
     */
    @PostMapping("/{id}/assets/{assetId}/audio")
    public ApiResponse<StoryDetailResponse> uploadCharacterAudio(
            @PathVariable Long id,
            @PathVariable Long assetId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success("Character audio uploaded", storyService.uploadCharacterAudio(id, assetId, SecurityUtils.getCurrentUserId(), file));
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
