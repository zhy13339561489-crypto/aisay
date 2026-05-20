package com.aisay.manga.service.impl;

import com.aisay.manga.dto.ai.StoryOutlineGenerateRequest;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StoryOutlineReviseResponse;
import com.aisay.manga.dto.ai.StoryVolumeOutlineGenerateRequest;
import com.aisay.manga.dto.ai.StoryVolumeOutlineGenerateResponse;
import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.Story;
import com.aisay.manga.entity.StoryVolumeOutline;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.repository.StoryVolumeOutlineMapper;
import com.aisay.manga.service.StoryService;
import com.aisay.manga.utils.AiEngineClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class StoryServiceImpl implements StoryService {

    private static final String STORY_STATUS_DRAFT = "draft";

    private static final String OUTLINE_STYLE = "story_outline";

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final StoryVolumeOutlineMapper storyVolumeOutlineMapper;

    private final AiEngineClient aiEngineClient;

    /**
     * 作用：注入故事、角色、会话、分卷大纲数据访问对象，以及 Python AI 引擎客户端。
     * 调用方：Spring 容器启动时自动构造 StoryServiceImpl。
     */
    public StoryServiceImpl(
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            StoryVolumeOutlineMapper storyVolumeOutlineMapper,
            AiEngineClient aiEngineClient
    ) {
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.storyVolumeOutlineMapper = storyVolumeOutlineMapper;
        this.aiEngineClient = aiEngineClient;
    }

    /**
     * 作用：调用 Python 生成剧情大纲，并新建漫剧记录及其角色设定。
     * 调用方：StoryController#generateStory。
     */
    @Override
    @Transactional
    public StoryResponse generateStory(Long userId, StoryGenerateRequest request) {
        StoryOutlineGenerateResponse outline = aiEngineClient.generateStoryOutline(new StoryOutlineGenerateRequest(
                userId,
                request.getGenre(),
                request.getPlot()
        ));
        LocalDateTime now = LocalDateTime.now();

        Story story = new Story();
        story.setUserId(userId);
        story.setTitle(resolveText(outline.getNovelName(), request.getGenre() + " story outline"));
        story.setGenre(request.getGenre());
        story.setStyle(OUTLINE_STYLE);
        story.setSynopsis(resolveText(outline.getStorySummary(), request.getPlot()));
        story.setFullContent(resolveText(outline.getOutline(), "Python AI engine did not return an outline."));
        story.setStatus(STORY_STATUS_DRAFT);
        story.setViewCount(0);
        story.setLikeCount(0);
        story.setCreatedAt(now);
        story.setUpdatedAt(now);
        storyMapper.insert(story);
        saveMainCharacters(story.getId(), outline.getMainCharacters());

        return toStoryResponse(story);
    }

    /**
     * 作用：调用 Python 大纲修改接口，根据用户修改意见更新故事摘要、大纲和角色设定。
     * 调用方：StoryController#reviseStoryOutline。
     */
    @Override
    @Transactional
    public StoryDetailResponse reviseStoryOutline(Long storyId, Long userId, StoryOutlineReviseRequest request) {
        Story story = getOwnedStory(storyId, userId);
        StoryOutlineReviseResponse revised = aiEngineClient.reviseStoryOutline(new com.aisay.manga.dto.ai.StoryOutlineReviseRequest(
                userId,
                story.getId(),
                story.getTitle(),
                story.getSynopsis(),
                story.getFullContent(),
                request.getSuggestion()
        ));

        story.setSynopsis(resolveText(revised.getStorySummary(), story.getSynopsis()));
        story.setFullContent(resolveText(revised.getOutline(), story.getFullContent()));
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        if (revised.getMainCharacters() != null && !revised.getMainCharacters().isEmpty()) {
            characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, storyId));
            saveMainCharacters(storyId, revised.getMainCharacters());
        }

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：保存用户手动编辑后的故事摘要、完整大纲和角色设定。
     * 调用方：StoryController#updateStoryDetail。
     */
    @Override
    @Transactional
    public StoryDetailResponse updateStoryDetail(Long storyId, Long userId, StoryDetailUpdateRequest request) {
        Story story = getOwnedStory(storyId, userId);
        if (request.getSynopsis() != null) {
            story.setSynopsis(request.getSynopsis());
        }
        if (request.getFullContent() != null) {
            story.setFullContent(request.getFullContent());
        }
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        if (request.getCharacters() != null) {
            characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, storyId));
            saveManualCharacters(storyId, request.getCharacters());
        }

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：调用 Python 生成分卷大纲，清空旧分卷记录后保存新分卷记录。
     * 调用方：StoryController#generateVolumeOutline。
     */
    @Override
    @Transactional
    public StoryDetailResponse generateVolumeOutline(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));
        StoryVolumeOutlineGenerateResponse response = aiEngineClient.generateVolumeOutline(new StoryVolumeOutlineGenerateRequest(
                userId,
                storyId,
                story.getTitle(),
                story.getSynopsis(),
                story.getFullContent(),
                characters.stream().map(this::toMainCharacterSetting).toList()
        ));

        storyVolumeOutlineMapper.delete(new LambdaQueryWrapper<StoryVolumeOutline>().eq(StoryVolumeOutline::getStoryId, storyId));
        saveVolumeOutlines(storyId, response.getVolumes());
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：查询故事详情，并组合角色设定、分卷大纲和场景占位列表返回给前端。
     * 调用方：StoryController#getStoryDetail、reviseStoryOutline、updateStoryDetail、generateVolumeOutline。
     */
    @Override
    public StoryDetailResponse getStoryDetail(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));
        List<StoryVolumeOutline> volumeOutlines = storyVolumeOutlineMapper.selectList(new LambdaQueryWrapper<StoryVolumeOutline>()
                .eq(StoryVolumeOutline::getStoryId, storyId)
                .orderByAsc(StoryVolumeOutline::getVolumeNumber)
                .orderByAsc(StoryVolumeOutline::getId));

        StoryDetailResponse response = new StoryDetailResponse();
        fillStoryResponse(response, story);
        response.setFullContent(story.getFullContent());
        response.setVolumeOutlines(volumeOutlines.stream().map(this::toVolumeOutlineItem).toList());
        response.setCharacters(characters.stream().map(this::toCharacterItem).toList());
        response.setScenes(List.of());
        return response;
    }

    /**
     * 作用：分页查询当前用户的故事列表。
     * 调用方：StoryController#getUserStories。
     */
    @Override
    public Page<StoryResponse> getUserStories(Long userId, int page, int size) {
        Page<Story> storyPage = storyMapper.selectPageByUserId(new Page<>(Math.max(page, 1), Math.max(size, 1)), userId);
        Page<StoryResponse> responsePage = new Page<>(storyPage.getCurrent(), storyPage.getSize(), storyPage.getTotal());
        responsePage.setRecords(storyPage.getRecords().stream().map(this::toStoryResponse).toList());
        return responsePage;
    }

    /**
     * 作用：更新故事基础字段，包括标题、题材、风格和摘要。
     * 调用方：StoryController#updateStory。
     */
    @Override
    @Transactional
    public StoryResponse updateStory(Long storyId, Long userId, StoryUpdateRequest request) {
        Story story = getOwnedStory(storyId, userId);
        if (request.getTitle() != null) {
            story.setTitle(request.getTitle());
        }
        if (request.getGenre() != null) {
            story.setGenre(request.getGenre());
        }
        if (request.getStyle() != null) {
            story.setStyle(request.getStyle());
        }
        if (request.getSynopsis() != null) {
            story.setSynopsis(request.getSynopsis());
        }
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        return toStoryResponse(story);
    }

    /**
     * 作用：删除当前用户拥有的故事记录。
     * 调用方：StoryController#deleteStory。
     */
    @Override
    @Transactional
    public void deleteStory(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        storyMapper.deleteById(story.getId());
    }

    /**
     * 作用：保存 AI 返回的结构化主要角色设定。
     * 调用方：generateStory、reviseStoryOutline。
     */
    private void saveMainCharacters(Long storyId, List<StoryOutlineGenerateResponse.MainCharacterSetting> mainCharacters) {
        if (mainCharacters == null || mainCharacters.isEmpty()) {
            return;
        }

        mainCharacters.forEach(setting -> {
            Character character = new Character();
            character.setStoryId(storyId);
            character.setName(resolveText(setting.getName(), "Unnamed character"));
            character.setRole(resolveText(setting.getRole(), "main character"));
            character.setDescription(setting.getDescription());
            character.setPersonality(setting.getPersonality());
            character.setAppearance(setting.getAppearance());
            characterMapper.insert(character);
        });
    }

    /**
     * 作用：保存用户手动编辑后的角色设定。
     * 调用方：updateStoryDetail。
     */
    private void saveManualCharacters(Long storyId, List<StoryDetailUpdateRequest.CharacterUpdateItem> characters) {
        characters.stream()
                .filter(character -> character.getName() != null && !character.getName().isBlank())
                .forEach(setting -> {
                    Character character = new Character();
                    character.setStoryId(storyId);
                    character.setName(resolveText(setting.getName(), "Unnamed character"));
                    character.setRole(resolveText(setting.getRole(), "main character"));
                    character.setDescription(setting.getDescription());
                    character.setPersonality(setting.getPersonality());
                    character.setAppearance(setting.getAppearance());
                    characterMapper.insert(character);
                });
    }

    /**
     * 作用：将 Python 返回的分卷大纲列表保存到 story_volume_outlines 表。
     * 调用方：generateVolumeOutline。
     */
    private void saveVolumeOutlines(Long storyId, List<StoryVolumeOutlineGenerateResponse.VolumeOutlineItem> volumes) {
        if (volumes == null || volumes.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < volumes.size(); index++) {
            StoryVolumeOutlineGenerateResponse.VolumeOutlineItem item = volumes.get(index);
            StoryVolumeOutline volume = new StoryVolumeOutline();
            volume.setStoryId(storyId);
            volume.setVolumeNumber(item.getVolumeNumber() == null ? index + 1 : item.getVolumeNumber());
            volume.setTitle(resolveText(item.getTitle(), "Volume " + volume.getVolumeNumber()));
            volume.setSummary(item.getSummary());
            volume.setContent(item.getContent());
            volume.setEndingHook(item.getEndingHook());
            volume.setCreatedAt(now);
            volume.setUpdatedAt(now);
            storyVolumeOutlineMapper.insert(volume);
        }
    }

    /**
     * 作用：校验故事存在且属于当前用户。
     * 调用方：reviseStoryOutline、updateStoryDetail、generateVolumeOutline、getStoryDetail、updateStory、deleteStory。
     */
    private Story getOwnedStory(Long storyId, Long userId) {
        Story story = storyMapper.selectById(storyId);
        if (story == null) {
            throw new NoSuchElementException("Story not found");
        }
        if (!story.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to access this story");
        }
        return story;
    }

    /**
     * 作用：将 Story 实体转换为故事列表/基础信息响应 DTO。
     * 调用方：generateStory、getUserStories、updateStory。
     */
    private StoryResponse toStoryResponse(Story story) {
        StoryResponse response = new StoryResponse();
        fillStoryResponse(response, story);
        return response;
    }

    /**
     * 作用：为空字符串或空值提供兜底文本。
     * 调用方：generateStory、reviseStoryOutline、saveMainCharacters、saveManualCharacters、saveVolumeOutlines。
     */
    private String resolveText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    /**
     * 作用：把 Story 公共字段填充到 StoryResponse 及其子类 StoryDetailResponse。
     * 调用方：toStoryResponse、getStoryDetail。
     */
    private void fillStoryResponse(StoryResponse response, Story story) {
        response.setId(story.getId());
        response.setTitle(story.getTitle());
        response.setGenre(story.getGenre());
        response.setStyle(story.getStyle());
        response.setSynopsis(story.getSynopsis());
        response.setStatus(story.getStatus());
        response.setCoverImagePath(story.getCoverImagePath());
        response.setCreatedAt(story.getCreatedAt());
    }

    /**
     * 作用：将角色实体转换为故事详情中的角色 DTO。
     * 调用方：getStoryDetail。
     */
    private StoryDetailResponse.CharacterItem toCharacterItem(Character character) {
        return new StoryDetailResponse.CharacterItem(
                character.getId(),
                character.getName(),
                character.getRole(),
                character.getDescription(),
                character.getPersonality(),
                character.getAppearance()
        );
    }

    /**
     * 作用：将数据库角色实体转换为 Python 分卷大纲接口需要的角色设定 DTO。
     * 调用方：generateVolumeOutline。
     */
    private StoryOutlineGenerateResponse.MainCharacterSetting toMainCharacterSetting(Character character) {
        return new StoryOutlineGenerateResponse.MainCharacterSetting(
                character.getName(),
                character.getRole(),
                character.getDescription(),
                character.getPersonality(),
                character.getAppearance()
        );
    }

    /**
     * 作用：将分卷大纲实体转换为故事详情中的分卷大纲 DTO。
     * 调用方：getStoryDetail。
     */
    private StoryDetailResponse.VolumeOutlineItem toVolumeOutlineItem(StoryVolumeOutline volume) {
        return new StoryDetailResponse.VolumeOutlineItem(
                volume.getId(),
                volume.getVolumeNumber(),
                volume.getTitle(),
                volume.getSummary(),
                volume.getContent(),
                volume.getEndingHook()
        );
    }
}
