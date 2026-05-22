package com.aisay.manga.service.impl;

import com.aisay.manga.dto.request.AiPromptParameterRequest;
import com.aisay.manga.dto.request.AiPromptRequest;
import com.aisay.manga.dto.response.AiPromptParameterResponse;
import com.aisay.manga.dto.response.AiPromptResponse;
import com.aisay.manga.entity.AiPrompt;
import com.aisay.manga.entity.AiPromptParameter;
import com.aisay.manga.repository.AiPromptMapper;
import com.aisay.manga.repository.AiPromptParameterMapper;
import com.aisay.manga.service.AiPromptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl implements AiPromptService {

    private final AiPromptMapper aiPromptMapper;
    private final AiPromptParameterMapper aiPromptParameterMapper;

    /**
     * 作用：查询 Prompt 列表，默认按分类和 prompt_key 排序，并附带参数详情。
     * 调用方：AiPromptController.listPrompts。
     */
    @Override
    public List<AiPromptResponse> listPrompts(String category, Boolean enabled, String keyword) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<AiPrompt>()
                .eq(category != null && !category.isBlank(), AiPrompt::getCategory, normalizeNullable(category))
                .eq(enabled != null, AiPrompt::getEnabled, enabled)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(AiPrompt::getPromptKey, keyword.trim())
                        .or()
                        .like(AiPrompt::getBasePromptKey, keyword.trim())
                        .or()
                        .like(AiPrompt::getPromptName, keyword.trim()))
                .orderByAsc(AiPrompt::getCategory)
                .orderByAsc(AiPrompt::getBasePromptKey)
                .orderByAsc(AiPrompt::getPromptScope)
                .orderByDesc(AiPrompt::getPriority)
                .orderByAsc(AiPrompt::getPromptKey);
        return aiPromptMapper.selectList(wrapper).stream()
                .map(this::toResponseWithParameters)
                .toList();
    }

    /**
     * 作用：按 ID 查询 Prompt 详情。
     * 调用方：AiPromptController.getPrompt。
     */
    @Override
    public AiPromptResponse getPrompt(Long id) {
        AiPrompt prompt = aiPromptMapper.selectById(id);
        if (prompt == null) {
            throw new NoSuchElementException("Prompt 不存在");
        }
        return toResponseWithParameters(prompt);
    }

    /**
     * 作用：创建 Prompt 与参数说明，prompt_key 全局唯一。
     * 调用方：AiPromptController.createPrompt。
     */
    @Override
    @Transactional
    public AiPromptResponse createPrompt(AiPromptRequest request) {
        String promptKey = normalizeRequired(request.getPromptKey(), "Prompt Key 不能为空");
        ensureUniquePromptKey(null, promptKey);

        AiPrompt prompt = new AiPrompt();
        applyPromptFields(prompt, request, promptKey);
        aiPromptMapper.insert(prompt);
        replaceParameters(prompt.getId(), request.getParameters());
        return getPrompt(prompt.getId());
    }

    /**
     * 作用：更新 Prompt 与参数说明，参数采用整体替换，避免前端编辑复杂 diff。
     * 调用方：AiPromptController.updatePrompt。
     */
    @Override
    @Transactional
    public AiPromptResponse updatePrompt(Long id, AiPromptRequest request) {
        AiPrompt prompt = aiPromptMapper.selectById(id);
        if (prompt == null) {
            throw new NoSuchElementException("Prompt 不存在");
        }

        String promptKey = normalizeRequired(request.getPromptKey(), "Prompt Key 不能为空");
        ensureUniquePromptKey(id, promptKey);
        applyPromptFields(prompt, request, promptKey);
        aiPromptMapper.updateById(prompt);
        replaceParameters(id, request.getParameters());
        return getPrompt(id);
    }

    /**
     * 作用：删除 Prompt 及其参数说明。
     * 调用方：AiPromptController.deletePrompt。
     */
    @Override
    @Transactional
    public void deletePrompt(Long id) {
        AiPrompt prompt = aiPromptMapper.selectById(id);
        if (prompt == null) {
            throw new NoSuchElementException("Prompt 不存在");
        }
        aiPromptParameterMapper.delete(new LambdaQueryWrapper<AiPromptParameter>()
                .eq(AiPromptParameter::getPromptId, id));
        aiPromptMapper.deleteById(id);
    }

    private void applyPromptFields(AiPrompt prompt, AiPromptRequest request, String promptKey) {
        String promptScope = normalizePromptScope(request.getPromptScope());
        String basePromptKey = normalizeBasePromptKey(promptKey, promptScope, request.getBasePromptKey());
        String matchGenre = normalizeNullable(request.getMatchGenre());
        String matchStyle = normalizeNullable(request.getMatchStyle());
        if ("DEFAULT".equals(promptScope)) {
            matchGenre = null;
            matchStyle = null;
        } else if (matchGenre == null && matchStyle == null) {
            throw new IllegalArgumentException("特定 Prompt 至少需要设置题材或风格匹配条件");
        }

        prompt.setPromptKey(promptKey);
        prompt.setBasePromptKey(basePromptKey);
        prompt.setPromptScope(promptScope);
        prompt.setMatchGenre(matchGenre);
        prompt.setMatchStyle(matchStyle);
        prompt.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        prompt.setPromptName(normalizeRequired(request.getPromptName(), "Prompt 名称不能为空"));
        prompt.setCategory(normalizeNullable(request.getCategory()));
        prompt.setDescription(normalizeNullable(request.getDescription()));
        prompt.setTemplateContent(normalizeRequired(request.getTemplateContent(), "Prompt 模板内容不能为空"));
        prompt.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private void replaceParameters(Long promptId, List<AiPromptParameterRequest> requests) {
        aiPromptParameterMapper.delete(new LambdaQueryWrapper<AiPromptParameter>()
                .eq(AiPromptParameter::getPromptId, promptId));

        if (requests == null) {
            return;
        }

        int fallbackOrder = 0;
        for (AiPromptParameterRequest request : requests) {
            AiPromptParameter parameter = new AiPromptParameter();
            parameter.setPromptId(promptId);
            parameter.setDirection(normalizeRequired(request.getDirection(), "参数方向不能为空").toUpperCase());
            parameter.setParamKey(normalizeRequired(request.getParamKey(), "参数标识不能为空"));
            parameter.setParamName(normalizeRequired(request.getParamName(), "参数名称不能为空"));
            parameter.setDataType(normalizeRequired(request.getDataType(), "参数类型不能为空"));
            parameter.setRequiredFlag(request.getRequiredFlag() == null || request.getRequiredFlag());
            parameter.setDescription(normalizeNullable(request.getDescription()));
            parameter.setExampleValue(normalizeNullable(request.getExampleValue()));
            parameter.setSortOrder(request.getSortOrder() == null ? fallbackOrder : request.getSortOrder());
            aiPromptParameterMapper.insert(parameter);
            fallbackOrder += 10;
        }
    }

    private void ensureUniquePromptKey(Long currentId, String promptKey) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptKey, promptKey)
                .ne(currentId != null, AiPrompt::getId, currentId);
        if (aiPromptMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("Prompt Key 已存在");
        }
    }

    private AiPromptResponse toResponseWithParameters(AiPrompt prompt) {
        List<AiPromptParameterResponse> parameters = aiPromptParameterMapper.selectList(
                        new LambdaQueryWrapper<AiPromptParameter>()
                                .eq(AiPromptParameter::getPromptId, prompt.getId())
                                .orderByAsc(AiPromptParameter::getDirection)
                                .orderByAsc(AiPromptParameter::getSortOrder)
                                .orderByAsc(AiPromptParameter::getId)
                ).stream()
                .map(this::toParameterResponse)
                .toList();

        return new AiPromptResponse(
                prompt.getId(),
                prompt.getPromptKey(),
                prompt.getBasePromptKey(),
                prompt.getPromptScope(),
                prompt.getMatchGenre(),
                prompt.getMatchStyle(),
                prompt.getPriority(),
                prompt.getPromptName(),
                prompt.getCategory(),
                prompt.getDescription(),
                prompt.getTemplateContent(),
                prompt.getEnabled(),
                prompt.getCreatedAt(),
                prompt.getUpdatedAt(),
                parameters
        );
    }

    private AiPromptParameterResponse toParameterResponse(AiPromptParameter parameter) {
        return new AiPromptParameterResponse(
                parameter.getId(),
                parameter.getDirection(),
                parameter.getParamKey(),
                parameter.getParamName(),
                parameter.getDataType(),
                parameter.getRequiredFlag(),
                parameter.getDescription(),
                parameter.getExampleValue(),
                parameter.getSortOrder(),
                parameter.getCreatedAt(),
                parameter.getUpdatedAt()
        );
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizePromptScope(String promptScope) {
        if (promptScope == null || promptScope.isBlank()) {
            return "DEFAULT";
        }
        String normalized = promptScope.trim().toUpperCase();
        if (!"DEFAULT".equals(normalized) && !"SPECIFIC".equals(normalized)) {
            throw new IllegalArgumentException("Prompt 作用域只能是 DEFAULT 或 SPECIFIC");
        }
        return normalized;
    }

    private String normalizeBasePromptKey(String promptKey, String promptScope, String basePromptKey) {
        if ("DEFAULT".equals(promptScope)) {
            return promptKey;
        }
        return normalizeRequired(basePromptKey, "特定 Prompt 必须指定基础 Prompt Key");
    }
}
