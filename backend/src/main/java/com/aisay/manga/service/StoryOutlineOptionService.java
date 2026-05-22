package com.aisay.manga.service;

import com.aisay.manga.dto.request.StoryOutlineOptionRequest;
import com.aisay.manga.dto.response.StoryOutlineOptionResponse;

import java.util.List;

public interface StoryOutlineOptionService {

    /**
     * 作用：按类型和启用状态查询剧情大纲配置项。
     * 调用方：StoryOutlineOptionController.listOptions。
     */
    List<StoryOutlineOptionResponse> listOptions(String type, Boolean enabled);

    /**
     * 作用：新建题材或漫剧风格配置项。
     * 调用方：StoryOutlineOptionController.createOption。
     */
    StoryOutlineOptionResponse createOption(StoryOutlineOptionRequest request);

    /**
     * 作用：更新题材或漫剧风格配置项。
     * 调用方：StoryOutlineOptionController.updateOption。
     */
    StoryOutlineOptionResponse updateOption(Long id, StoryOutlineOptionRequest request);

    /**
     * 作用：删除题材或漫剧风格配置项。
     * 调用方：StoryOutlineOptionController.deleteOption。
     */
    void deleteOption(Long id);
}
