package com.aisay.manga.service;

import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface StoryService {

    /**
     * 作用：生成剧情大纲并新建漫剧。
     * 调用方：StoryController#generateStory。
     */
    StoryResponse generateStory(Long userId, StoryGenerateRequest request);

    /**
     * 作用：根据修改意见调用 AI 修改剧情大纲。
     * 调用方：StoryController#reviseStoryOutline。
     */
    StoryDetailResponse reviseStoryOutline(Long storyId, Long userId, StoryOutlineReviseRequest request);

    /**
     * 作用：手动保存故事详情和角色设定。
     * 调用方：StoryController#updateStoryDetail。
     */
    StoryDetailResponse updateStoryDetail(Long storyId, Long userId, StoryDetailUpdateRequest request);

    /**
     * 作用：生成并保存分卷大纲。
     * 调用方：StoryController#generateVolumeOutline。
     */
    StoryDetailResponse generateVolumeOutline(Long storyId, Long userId);

    /**
     * 作用：查询故事详情。
     * 调用方：StoryController#getStoryDetail。
     */
    StoryDetailResponse getStoryDetail(Long storyId, Long userId);

    /**
     * 作用：分页查询用户故事列表。
     * 调用方：StoryController#getUserStories。
     */
    Page<StoryResponse> getUserStories(Long userId, int page, int size);

    /**
     * 作用：更新故事基础信息。
     * 调用方：StoryController#updateStory。
     */
    StoryResponse updateStory(Long storyId, Long userId, StoryUpdateRequest request);

    /**
     * 作用：删除用户拥有的故事。
     * 调用方：StoryController#deleteStory。
     */
    void deleteStory(Long storyId, Long userId);
}
