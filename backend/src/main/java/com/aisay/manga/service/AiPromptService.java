package com.aisay.manga.service;

import com.aisay.manga.dto.request.AiPromptRequest;
import com.aisay.manga.dto.response.AiPromptResponse;

import java.util.List;

public interface AiPromptService {

    /**
     * 作用：查询 Prompt 配置列表，可按分类、启用状态和关键字过滤。
     * 调用方：AiPromptController.listPrompts。
     */
    List<AiPromptResponse> listPrompts(String category, Boolean enabled, String keyword);

    /**
     * 作用：查询单个 Prompt 详情，包括输入/输出参数说明。
     * 调用方：AiPromptController.getPrompt。
     */
    AiPromptResponse getPrompt(Long id);

    /**
     * 作用：新建 Prompt 模板和参数说明。
     * 调用方：AiPromptController.createPrompt。
     */
    AiPromptResponse createPrompt(AiPromptRequest request);

    /**
     * 作用：更新 Prompt 模板和参数说明。
     * 调用方：AiPromptController.updatePrompt。
     */
    AiPromptResponse updatePrompt(Long id, AiPromptRequest request);

    /**
     * 作用：删除 Prompt 模板及其参数说明。
     * 调用方：AiPromptController.deletePrompt。
     */
    void deletePrompt(Long id);
}
