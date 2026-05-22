package com.aisay.manga.controller;

import com.aisay.manga.dto.request.AiPromptRequest;
import com.aisay.manga.dto.response.AiPromptResponse;
import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.service.AiPromptService;
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
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class AiPromptController {

    private final AiPromptService aiPromptService;

    /**
     * 作用：查询 Prompt 管理列表，可按分类、启用状态和关键字过滤。
     * 调用方：前端 Prompt 管理页面。
     */
    @GetMapping
    public ApiResponse<List<AiPromptResponse>> listPrompts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(aiPromptService.listPrompts(category, enabled, keyword));
    }

    /**
     * 作用：查询单个 Prompt 详情，包括输入/输出参数详情。
     * 调用方：前端 Prompt 管理页面编辑弹窗。
     */
    @GetMapping("/{id}")
    public ApiResponse<AiPromptResponse> getPrompt(@PathVariable Long id) {
        return ApiResponse.success(aiPromptService.getPrompt(id));
    }

    /**
     * 作用：新增 Prompt 模板。
     * 调用方：前端 Prompt 管理页面。
     */
    @PostMapping
    public ApiResponse<AiPromptResponse> createPrompt(@Valid @RequestBody AiPromptRequest request) {
        return ApiResponse.success("Prompt 已创建", aiPromptService.createPrompt(request));
    }

    /**
     * 作用：修改 Prompt 模板。
     * 调用方：前端 Prompt 管理页面。
     */
    @PutMapping("/{id}")
    public ApiResponse<AiPromptResponse> updatePrompt(
            @PathVariable Long id,
            @Valid @RequestBody AiPromptRequest request
    ) {
        return ApiResponse.success("Prompt 已更新", aiPromptService.updatePrompt(id, request));
    }

    /**
     * 作用：删除 Prompt 模板。
     * 调用方：前端 Prompt 管理页面。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePrompt(@PathVariable Long id) {
        aiPromptService.deletePrompt(id);
        return ApiResponse.success("Prompt 已删除", null);
    }
}
