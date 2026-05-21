package com.aisay.manga.service.impl;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.dto.ai.AiStoryTaskMessage;
import com.aisay.manga.dto.ai.AiStoryTaskResultMessage;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StoryVolumeOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StoryVolumeSectionGenerateResponse;
import com.aisay.manga.dto.request.StoryDetailUpdateRequest;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.request.StoryVolumeOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryVolumeOutlineUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.Story;
import com.aisay.manga.entity.StoryVolumeOutline;
import com.aisay.manga.entity.StoryVolumeSection;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.repository.StoryVolumeOutlineMapper;
import com.aisay.manga.repository.StoryVolumeSectionMapper;
import com.aisay.manga.service.StoryService;
import com.aisay.manga.utils.AiStoryTaskPublisher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoryServiceImpl implements StoryService {

    private static final String STORY_STATUS_DRAFT = "draft";

    private static final String STORY_STATUS_GENERATING = "generating";

    private static final String STORY_STATUS_REVISING = "revising";

    private static final String STORY_STATUS_VOLUME_PENDING = "volume_pending";

    private static final String STORY_STATUS_VOLUME_STORY_PENDING = "volume_story_pending";

    private static final String STORY_STATUS_VOLUME_SECTION_PENDING = "volume_section_pending";

    private static final String STORY_STATUS_FAILED = "failed";

    private static final String OUTLINE_STYLE = "story_outline";

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final StoryVolumeOutlineMapper storyVolumeOutlineMapper;

    private final StoryVolumeSectionMapper storyVolumeSectionMapper;

    private final AiStoryTaskPublisher aiStoryTaskPublisher;

    /**
     * 作用：注入故事、角色、分卷大纲数据访问对象，以及 RabbitMQ AI 任务发布器。
     * 调用方：Spring 容器启动时自动构造 StoryServiceImpl。
     */
    public StoryServiceImpl(
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            StoryVolumeOutlineMapper storyVolumeOutlineMapper,
            StoryVolumeSectionMapper storyVolumeSectionMapper,
            AiStoryTaskPublisher aiStoryTaskPublisher
    ) {
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.storyVolumeOutlineMapper = storyVolumeOutlineMapper;
        this.storyVolumeSectionMapper = storyVolumeSectionMapper;
        this.aiStoryTaskPublisher = aiStoryTaskPublisher;
    }

    /**
     * 作用：新建一个“生成中”的故事占位记录，并把剧情大纲生成任务投递给 Python worker。
     * 调用方：StoryController#generateStory。
     */
    @Override
    @Transactional
    public StoryResponse generateStory(Long userId, StoryGenerateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Story story = new Story();
        story.setUserId(userId);
        story.setTitle(resolveText(request.getGenre(), "新故事") + " 剧情大纲生成中");
        story.setGenre(request.getGenre());
        story.setStyle(OUTLINE_STYLE);
        story.setSynopsis(resolveText(request.getPlot(), "AI 正在根据题材生成剧情大纲，请稍后刷新。"));
        story.setFullContent(null);
        story.setStatus(STORY_STATUS_GENERATING);
        story.setViewCount(0);
        story.setLikeCount(0);
        story.setCreatedAt(now);
        story.setUpdatedAt(now);
        storyMapper.insert(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_STORY_GENERATE, userId, story.getId());
        message.setGenre(request.getGenre());
        message.setPlot(request.getPlot());
        publishAfterCommit(message);

        return toStoryResponse(story);
    }

    /**
     * 作用：把故事标记为大纲修改中，并异步投递自动修改剧情大纲任务。
     * 调用方：StoryController#reviseStoryOutline。
     */
    @Override
    @Transactional
    public StoryDetailResponse reviseStoryOutline(Long storyId, Long userId, StoryOutlineReviseRequest request) {
        Story story = getOwnedStory(storyId, userId);
        story.setStatus(STORY_STATUS_REVISING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_STORY_REVISE, userId, storyId);
        message.setTitle(story.getTitle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setSuggestion(request.getSuggestion());
        publishAfterCommit(message);

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
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        if (request.getCharacters() != null) {
            characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, storyId));
            saveManualCharacters(storyId, request.getCharacters());
        }

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：把故事标记为分卷处理中，并异步投递分卷大纲生成任务。
     * 调用方：StoryController#generateVolumeOutline。
     */
    @Override
    @Transactional
    public StoryDetailResponse generateVolumeOutline(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        if (story.getFullContent() == null || story.getFullContent().isBlank()) {
            throw new IllegalArgumentException("Please generate or fill in the story outline before generating volume outlines.");
        }

        story.setStatus(STORY_STATUS_VOLUME_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        storyVolumeOutlineMapper.delete(new LambdaQueryWrapper<StoryVolumeOutline>().eq(StoryVolumeOutline::getStoryId, storyId));

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_VOLUME_GENERATE, userId, storyId);
        message.setTitle(story.getTitle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        publishAfterCommit(message);

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：读取现有分卷大纲，并异步投递自动重写分卷大纲任务。
     * 调用方：StoryController#reviseVolumeOutline。
     */
    @Override
    @Transactional
    public StoryDetailResponse reviseVolumeOutline(Long storyId, Long userId, StoryVolumeOutlineReviseRequest request) {
        Story story = getOwnedStory(storyId, userId);
        List<StoryVolumeOutline> existingVolumes = getVolumeOutlineEntities(storyId);
        if (existingVolumes.isEmpty()) {
            throw new IllegalArgumentException("Please generate volume outlines before revising them.");
        }
        if (story.getFullContent() == null || story.getFullContent().isBlank()) {
            throw new IllegalArgumentException("Story outline is required before revising volume outlines.");
        }

        story.setStatus(STORY_STATUS_VOLUME_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_VOLUME_REVISE, userId, storyId);
        message.setTitle(story.getTitle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        message.setVolumeOutlines(existingVolumes.stream().map(this::toAiVolumeOutlineItem).toList());
        message.setSuggestion(request.getSuggestion());
        publishAfterCommit(message);

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：把指定分卷标记为小节生成任务，异步投递给 Python worker。
     * 调用方：StoryController#generateVolumeSections。
     */
    @Override
    @Transactional
    public StoryDetailResponse generateVolumeSections(Long storyId, Long volumeId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        StoryVolumeOutline volume = getOwnedVolume(storyId, volumeId);
        if (volume.getContent() == null || volume.getContent().isBlank()) {
            throw new IllegalArgumentException("Volume outline content is required before generating sections.");
        }

        story.setStatus(STORY_STATUS_VOLUME_SECTION_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        storyVolumeSectionMapper.delete(new LambdaQueryWrapper<StoryVolumeSection>()
                .eq(StoryVolumeSection::getVolumeId, volumeId));

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_VOLUME_SECTION_GENERATE, userId, storyId);
        message.setVolumeId(volumeId);
        message.setTitle(story.getTitle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        message.setVolumeOutlines(List.of(toAiVolumeOutlineItem(volume)));
        publishAfterCommit(message);

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：保存用户手动编辑后的分卷大纲列表，整体替换当前 story 的旧分卷。
     * 调用方：StoryController#updateVolumeOutlines。
     */
    @Override
    @Transactional
    public StoryDetailResponse updateVolumeOutlines(Long storyId, Long userId, StoryVolumeOutlineUpdateRequest request) {
        Story story = getOwnedStory(storyId, userId);
        List<StoryVolumeOutlineGenerateResponse.VolumeOutlineItem> volumes = request.getVolumes().stream()
                .map(this::toAiVolumeOutlineItem)
                .toList();

        replaceVolumeOutlines(storyId, volumes);
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：消费 Python 通过 RabbitMQ 返回的非对话 AI 任务结果，并按任务类型更新故事数据。
     * 调用方：AiStoryTaskResultListener#handleResult。
     */
    @Transactional
    public void applyStoryTaskResult(AiStoryTaskResultMessage result) {
        if (result == null || result.getStoryId() == null || result.getTaskType() == null) {
            return;
        }

        Story story = storyMapper.selectById(result.getStoryId());
        if (story == null || !story.getUserId().equals(result.getUserId())) {
            return;
        }

        if (!Boolean.TRUE.equals(result.getSuccess())) {
            markStoryFailed(story);
            return;
        }

        switch (result.getTaskType()) {
            case AiRabbitConstants.TASK_STORY_GENERATE -> applyGeneratedStoryOutline(story, result.getStoryOutline());
            case AiRabbitConstants.TASK_STORY_REVISE -> applyRevisedStoryOutline(story, result.getStoryOutline());
            case AiRabbitConstants.TASK_VOLUME_GENERATE -> applyGeneratedVolumeOutlineResult(story, result);
            case AiRabbitConstants.TASK_VOLUME_REVISE -> applyVolumeOutline(story, result.getVolumeOutline());
            case AiRabbitConstants.TASK_VOLUME_STORY_GENERATE -> applyVolumeStory(story, result);
            case AiRabbitConstants.TASK_VOLUME_SECTION_GENERATE -> applyGeneratedVolumeSectionResult(story, result);
            default -> {
                // Ignore unknown task types so one bad message does not block the listener.
            }
        }
    }

    /**
     * 作用：查询故事详情，并组合角色设定、分卷大纲和场景占位列表返回给前端。
     * 调用方：StoryController#getStoryDetail。
     */
    @Override
    public StoryDetailResponse getStoryDetail(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));
        List<StoryVolumeOutline> volumeOutlines = getVolumeOutlineEntities(storyId);
        Map<Long, List<StoryVolumeSection>> sectionMap = getVolumeSectionEntities(storyId).stream()
                .collect(Collectors.groupingBy(StoryVolumeSection::getVolumeId));

        StoryDetailResponse response = new StoryDetailResponse();
        fillStoryResponse(response, story);
        response.setFullContent(story.getFullContent());
        response.setVolumeOutlines(volumeOutlines.stream()
                .map(volume -> toVolumeOutlineItem(volume, sectionMap.getOrDefault(volume.getId(), List.of())))
                .toList());
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

    private AiStoryTaskMessage newTaskMessage(String taskType, Long userId, Long storyId) {
        AiStoryTaskMessage message = new AiStoryTaskMessage();
        message.setTaskId(UUID.randomUUID().toString());
        message.setTaskType(taskType);
        message.setUserId(userId);
        message.setStoryId(storyId);
        return message;
    }

    private void publishAfterCommit(AiStoryTaskMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aiStoryTaskPublisher.publish(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiStoryTaskPublisher.publish(message);
            }
        });
    }

    private void applyGeneratedStoryOutline(Story story, StoryOutlineGenerateResponse outline) {
        if (outline == null) {
            markStoryFailed(story);
            return;
        }

        story.setTitle(resolveText(outline.getNovelName(), story.getTitle()));
        story.setSynopsis(resolveText(outline.getStorySummary(), story.getSynopsis()));
        story.setFullContent(resolveText(outline.getOutline(), story.getFullContent()));
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, story.getId()));
        saveMainCharacters(story.getId(), outline.getMainCharacters());
    }

    private void applyRevisedStoryOutline(Story story, StoryOutlineGenerateResponse outline) {
        if (outline == null) {
            markStoryFailed(story);
            return;
        }

        story.setSynopsis(resolveText(outline.getStorySummary(), story.getSynopsis()));
        story.setFullContent(resolveText(outline.getOutline(), story.getFullContent()));
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        if (outline.getMainCharacters() != null && !outline.getMainCharacters().isEmpty()) {
            characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getStoryId, story.getId()));
            saveMainCharacters(story.getId(), outline.getMainCharacters());
        }
    }

    private void applyVolumeOutline(Story story, StoryVolumeOutlineGenerateResponse response) {
        if (response == null || response.getVolumes() == null || response.getVolumes().isEmpty()) {
            markStoryFailed(story);
            return;
        }

        replaceVolumeOutlines(story.getId(), response.getVolumes());
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private void applyGeneratedVolumeOutlineResult(Story story, AiStoryTaskResultMessage result) {
        if (Boolean.TRUE.equals(result.getPartial())) {
            applyPartialVolumeOutline(story, result.getVolumeOutline());
            return;
        }

        if (Boolean.TRUE.equals(result.getCompleted())) {
            story.setStatus(STORY_STATUS_DRAFT);
            story.setUpdatedAt(LocalDateTime.now());
            storyMapper.updateById(story);
            return;
        }

        applyVolumeOutline(story, result.getVolumeOutline());
    }

    private void applyPartialVolumeOutline(Story story, StoryVolumeOutlineGenerateResponse response) {
        if (response == null || response.getVolumes() == null || response.getVolumes().isEmpty()) {
            return;
        }

        response.getVolumes().forEach(volume -> replaceSingleVolumeOutline(story.getId(), volume));
        story.setStatus(STORY_STATUS_VOLUME_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private void markStoryFailed(Story story) {
        story.setStatus(STORY_STATUS_FAILED);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private void applyVolumeStory(Story story, AiStoryTaskResultMessage result) {
        if (result.getVolumeId() == null || result.getVolumeStory() == null || result.getVolumeStory().isBlank()) {
            markStoryFailed(story);
            return;
        }

        StoryVolumeOutline volume = storyVolumeOutlineMapper.selectById(result.getVolumeId());
        if (volume == null || !story.getId().equals(volume.getStoryId())) {
            return;
        }
        volume.setDetailedContent(result.getVolumeStory());
        volume.setUpdatedAt(LocalDateTime.now());
        storyVolumeOutlineMapper.updateById(volume);

        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private void applyGeneratedVolumeSectionResult(Story story, AiStoryTaskResultMessage result) {
        if (Boolean.TRUE.equals(result.getPartial())) {
            applyPartialVolumeSection(story, result.getVolumeId(), result.getVolumeSection());
            return;
        }

        if (Boolean.TRUE.equals(result.getCompleted())) {
            story.setStatus(STORY_STATUS_DRAFT);
            story.setUpdatedAt(LocalDateTime.now());
            storyMapper.updateById(story);
            return;
        }

        applyPartialVolumeSection(story, result.getVolumeId(), result.getVolumeSection());
    }

    private void applyPartialVolumeSection(Story story, Long volumeId, StoryVolumeSectionGenerateResponse response) {
        if (volumeId == null || response == null || response.getSections() == null || response.getSections().isEmpty()) {
            return;
        }

        StoryVolumeOutline volume = storyVolumeOutlineMapper.selectById(volumeId);
        if (volume == null || !story.getId().equals(volume.getStoryId())) {
            return;
        }

        response.getSections().forEach(section -> replaceSingleVolumeSection(story.getId(), volumeId, section));
        story.setStatus(STORY_STATUS_VOLUME_SECTION_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private List<StoryOutlineGenerateResponse.MainCharacterSetting> getMainCharacterSettings(Long storyId) {
        return characterMapper.selectList(new LambdaQueryWrapper<Character>()
                        .eq(Character::getStoryId, storyId)
                        .orderByAsc(Character::getId))
                .stream()
                .map(this::toMainCharacterSetting)
                .toList();
    }

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
            volume.setDetailedContent(null);
            volume.setCreatedAt(now);
            volume.setUpdatedAt(now);
            storyVolumeOutlineMapper.insert(volume);
        }
    }

    private void replaceVolumeOutlines(Long storyId, List<StoryVolumeOutlineGenerateResponse.VolumeOutlineItem> volumes) {
        storyVolumeOutlineMapper.delete(new LambdaQueryWrapper<StoryVolumeOutline>().eq(StoryVolumeOutline::getStoryId, storyId));
        saveVolumeOutlines(storyId, volumes);
    }

    private void replaceSingleVolumeOutline(Long storyId, StoryVolumeOutlineGenerateResponse.VolumeOutlineItem item) {
        if (item == null) {
            return;
        }

        Integer volumeNumber = item.getVolumeNumber();
        if (volumeNumber == null) {
            List<StoryVolumeOutline> existingVolumes = getVolumeOutlineEntities(storyId);
            volumeNumber = existingVolumes.size() + 1;
        }

        storyVolumeOutlineMapper.delete(new LambdaQueryWrapper<StoryVolumeOutline>()
                .eq(StoryVolumeOutline::getStoryId, storyId)
                .eq(StoryVolumeOutline::getVolumeNumber, volumeNumber));
        saveVolumeOutlines(storyId, List.of(new StoryVolumeOutlineGenerateResponse.VolumeOutlineItem(
                volumeNumber,
                item.getTitle(),
                item.getSummary(),
                item.getContent(),
                item.getEndingHook()
        )));
    }

    private List<StoryVolumeOutline> getVolumeOutlineEntities(Long storyId) {
        return storyVolumeOutlineMapper.selectList(new LambdaQueryWrapper<StoryVolumeOutline>()
                .eq(StoryVolumeOutline::getStoryId, storyId)
                .orderByAsc(StoryVolumeOutline::getVolumeNumber)
                .orderByAsc(StoryVolumeOutline::getId));
    }

    private List<StoryVolumeSection> getVolumeSectionEntities(Long storyId) {
        return storyVolumeSectionMapper.selectList(new LambdaQueryWrapper<StoryVolumeSection>()
                .eq(StoryVolumeSection::getStoryId, storyId)
                .orderByAsc(StoryVolumeSection::getVolumeId)
                .orderByAsc(StoryVolumeSection::getSectionNumber)
                .orderByAsc(StoryVolumeSection::getId));
    }

    private void replaceSingleVolumeSection(
            Long storyId,
            Long volumeId,
            StoryVolumeSectionGenerateResponse.VolumeSectionItem item
    ) {
        if (item == null) {
            return;
        }

        Integer sectionNumber = item.getSectionNumber();
        if (sectionNumber == null) {
            List<StoryVolumeSection> existingSections = storyVolumeSectionMapper.selectList(new LambdaQueryWrapper<StoryVolumeSection>()
                    .eq(StoryVolumeSection::getVolumeId, volumeId)
                    .orderByAsc(StoryVolumeSection::getSectionNumber));
            sectionNumber = existingSections.size() + 1;
        }

        storyVolumeSectionMapper.delete(new LambdaQueryWrapper<StoryVolumeSection>()
                .eq(StoryVolumeSection::getVolumeId, volumeId)
                .eq(StoryVolumeSection::getSectionNumber, sectionNumber));

        LocalDateTime now = LocalDateTime.now();
        StoryVolumeSection section = new StoryVolumeSection();
        section.setStoryId(storyId);
        section.setVolumeId(volumeId);
        section.setSectionNumber(sectionNumber);
        section.setTitle(resolveText(item.getTitle(), "Section " + sectionNumber));
        section.setSummary(item.getSummary());
        section.setContent(item.getContent());
        section.setEndingHook(item.getEndingHook());
        section.setCreatedAt(now);
        section.setUpdatedAt(now);
        storyVolumeSectionMapper.insert(section);
    }

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

    private StoryVolumeOutline getOwnedVolume(Long storyId, Long volumeId) {
        StoryVolumeOutline volume = storyVolumeOutlineMapper.selectById(volumeId);
        if (volume == null || !storyId.equals(volume.getStoryId())) {
            throw new NoSuchElementException("Volume outline not found");
        }
        return volume;
    }

    private StoryResponse toStoryResponse(Story story) {
        StoryResponse response = new StoryResponse();
        fillStoryResponse(response, story);
        return response;
    }

    private String resolveText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

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

    private StoryOutlineGenerateResponse.MainCharacterSetting toMainCharacterSetting(Character character) {
        return new StoryOutlineGenerateResponse.MainCharacterSetting(
                character.getName(),
                character.getRole(),
                character.getDescription(),
                character.getPersonality(),
                character.getAppearance()
        );
    }

    private StoryVolumeOutlineGenerateResponse.VolumeOutlineItem toAiVolumeOutlineItem(StoryVolumeOutline volume) {
        return new StoryVolumeOutlineGenerateResponse.VolumeOutlineItem(
                volume.getVolumeNumber(),
                volume.getTitle(),
                volume.getSummary(),
                volume.getContent(),
                volume.getEndingHook()
        );
    }

    private StoryVolumeOutlineGenerateResponse.VolumeOutlineItem toAiVolumeOutlineItem(StoryVolumeOutlineUpdateRequest.VolumeOutlineUpdateItem item) {
        return new StoryVolumeOutlineGenerateResponse.VolumeOutlineItem(
                item.getVolumeNumber(),
                item.getTitle(),
                item.getSummary(),
                item.getContent(),
                item.getEndingHook()
        );
    }

    private StoryDetailResponse.VolumeOutlineItem toVolumeOutlineItem(
            StoryVolumeOutline volume,
            List<StoryVolumeSection> sections
    ) {
        return new StoryDetailResponse.VolumeOutlineItem(
                volume.getId(),
                volume.getVolumeNumber(),
                volume.getTitle(),
                volume.getSummary(),
                volume.getContent(),
                volume.getEndingHook(),
                volume.getDetailedContent(),
                sections.stream().map(this::toVolumeSectionItem).toList()
        );
    }

    private StoryDetailResponse.VolumeSectionItem toVolumeSectionItem(StoryVolumeSection section) {
        return new StoryDetailResponse.VolumeSectionItem(
                section.getId(),
                section.getSectionNumber(),
                section.getTitle(),
                section.getSummary(),
                section.getContent(),
                section.getEndingHook()
        );
    }
}
