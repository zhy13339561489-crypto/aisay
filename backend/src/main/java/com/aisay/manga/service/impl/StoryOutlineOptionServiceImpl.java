package com.aisay.manga.service.impl;

import com.aisay.manga.dto.request.StoryOutlineOptionRequest;
import com.aisay.manga.dto.response.StoryOutlineOptionResponse;
import com.aisay.manga.entity.StoryOutlineOption;
import com.aisay.manga.repository.StoryOutlineOptionMapper;
import com.aisay.manga.service.StoryOutlineOptionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoryOutlineOptionServiceImpl implements StoryOutlineOptionService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("GENRE", "STYLE");

    private final StoryOutlineOptionMapper storyOutlineOptionMapper;

    /**
     * 作用：按类型和启用状态查询配置，默认按 sort_order 和 id 稳定排序。
     * 调用方：StoryOutlineOptionController.listOptions。
     */
    @Override
    public List<StoryOutlineOptionResponse> listOptions(String type, Boolean enabled) {
        LambdaQueryWrapper<StoryOutlineOption> wrapper = new LambdaQueryWrapper<StoryOutlineOption>()
                .eq(type != null && !type.isBlank(), StoryOutlineOption::getOptionType, normalizeType(type))
                .eq(enabled != null, StoryOutlineOption::getEnabled, enabled)
                .orderByAsc(StoryOutlineOption::getSortOrder)
                .orderByAsc(StoryOutlineOption::getId);
        return storyOutlineOptionMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 作用：创建新的题材或风格配置，并防止同类型下名称重复。
     * 调用方：StoryOutlineOptionController.createOption。
     */
    @Override
    @Transactional
    public StoryOutlineOptionResponse createOption(StoryOutlineOptionRequest request) {
        String type = normalizeType(request.getType());
        String name = normalizeName(request.getName());
        ensureUniqueName(null, type, name);

        StoryOutlineOption option = new StoryOutlineOption();
        option.setOptionType(type);
        option.setName(name);
        option.setDescription(normalizeNullable(request.getDescription()));
        option.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        option.setEnabled(request.getEnabled() == null || request.getEnabled());
        storyOutlineOptionMapper.insert(option);
        return toResponse(option);
    }

    /**
     * 作用：更新已有题材或风格配置，并同步校验重复名称。
     * 调用方：StoryOutlineOptionController.updateOption。
     */
    @Override
    @Transactional
    public StoryOutlineOptionResponse updateOption(Long id, StoryOutlineOptionRequest request) {
        StoryOutlineOption option = storyOutlineOptionMapper.selectById(id);
        if (option == null) {
            throw new NoSuchElementException("配置项不存在");
        }

        String type = normalizeType(request.getType());
        String name = normalizeName(request.getName());
        ensureUniqueName(id, type, name);

        option.setOptionType(type);
        option.setName(name);
        option.setDescription(normalizeNullable(request.getDescription()));
        option.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        option.setEnabled(request.getEnabled() == null || request.getEnabled());
        storyOutlineOptionMapper.updateById(option);
        return toResponse(storyOutlineOptionMapper.selectById(id));
    }

    /**
     * 作用：物理删除配置项；已生成故事中保存的是字符串，不受删除影响。
     * 调用方：StoryOutlineOptionController.deleteOption。
     */
    @Override
    @Transactional
    public void deleteOption(Long id) {
        StoryOutlineOption option = storyOutlineOptionMapper.selectById(id);
        if (option == null) {
            throw new NoSuchElementException("配置项不存在");
        }
        storyOutlineOptionMapper.deleteById(id);
    }

    /**
     * 作用：校验配置类型并统一转换为大写。
     * 调用方：listOptions、createOption、updateOption。
     */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("配置类型只能是 GENRE 或 STYLE");
        }
        return normalized;
    }

    /**
     * 作用：清理配置名称两端空白。
     * 调用方：createOption、updateOption。
     */
    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        return name.trim();
    }

    /**
     * 作用：清理可选文本字段两端空白，空字符串落库为 null。
     * 调用方：createOption、updateOption。
     */
    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 作用：确保同一种配置类型下名称唯一。
     * 调用方：createOption、updateOption。
     */
    private void ensureUniqueName(Long currentId, String type, String name) {
        LambdaQueryWrapper<StoryOutlineOption> wrapper = new LambdaQueryWrapper<StoryOutlineOption>()
                .eq(StoryOutlineOption::getOptionType, type)
                .eq(StoryOutlineOption::getName, name)
                .ne(currentId != null, StoryOutlineOption::getId, currentId);
        if (storyOutlineOptionMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("同类型下已存在相同名称的配置项");
        }
    }

    /**
     * 作用：将数据库实体转换为前端响应对象。
     * 调用方：listOptions、createOption、updateOption。
     */
    private StoryOutlineOptionResponse toResponse(StoryOutlineOption option) {
        return new StoryOutlineOptionResponse(
                option.getId(),
                option.getOptionType(),
                option.getName(),
                option.getDescription(),
                option.getSortOrder(),
                option.getEnabled(),
                option.getCreatedAt(),
                option.getUpdatedAt()
        );
    }
}
