package com.aisay.manga.controller;

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

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping("/generate")
    public ApiResponse<StoryResponse> generateStory(@Valid @RequestBody StoryGenerateRequest request) {
        return ApiResponse.success("漫剧生成成功", storyService.generateStory(SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/{id}/outline/revise")
    public ApiResponse<StoryDetailResponse> reviseStoryOutline(
            @PathVariable Long id,
            @Valid @RequestBody StoryOutlineReviseRequest request
    ) {
        return ApiResponse.success("大纲修改成功", storyService.reviseStoryOutline(id, SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<StoryDetailResponse> getStoryDetail(@PathVariable Long id) {
        return ApiResponse.success(storyService.getStoryDetail(id, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/list")
    public ApiResponse<Page<StoryResponse>> getUserStories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(storyService.getUserStories(SecurityUtils.getCurrentUserId(), page, size));
    }

    @PutMapping("/{id}")
    public ApiResponse<StoryResponse> updateStory(
            @PathVariable Long id,
            @Valid @RequestBody StoryUpdateRequest request
    ) {
        return ApiResponse.success("漫剧更新成功", storyService.updateStory(id, SecurityUtils.getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteStory(@PathVariable Long id) {
        storyService.deleteStory(id, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("漫剧删除成功", null);
    }
}
