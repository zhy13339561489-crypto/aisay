package com.aisay.manga.service;

import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface StoryService {

    StoryResponse generateStory(Long userId, StoryGenerateRequest request);

    StoryDetailResponse getStoryDetail(Long storyId, Long userId);

    Page<StoryResponse> getUserStories(Long userId, int page, int size);

    StoryResponse updateStory(Long storyId, Long userId, StoryUpdateRequest request);

    void deleteStory(Long storyId, Long userId);
}
