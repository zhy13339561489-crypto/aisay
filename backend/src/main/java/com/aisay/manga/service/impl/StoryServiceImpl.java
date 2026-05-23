package com.aisay.manga.service.impl;

import com.aisay.manga.config.AiRabbitConstants;
import com.aisay.manga.config.FeaturePermissionKeys;
import com.aisay.manga.dto.ai.AiStoryTaskMessage;
import com.aisay.manga.dto.ai.AiStoryTaskResultMessage;
import com.aisay.manga.dto.ai.StoryAssetReference;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StorySectionScriptGenerateResponse;
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
import com.aisay.manga.entity.StoryAsset;
import com.aisay.manga.entity.StorySectionScript;
import com.aisay.manga.entity.StorySectionAsset;
import com.aisay.manga.entity.StoryVolumeOutline;
import com.aisay.manga.entity.StoryVolumeSection;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.StoryAssetMapper;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.repository.StorySectionAssetMapper;
import com.aisay.manga.repository.StorySectionScriptMapper;
import com.aisay.manga.repository.StoryVolumeOutlineMapper;
import com.aisay.manga.repository.StoryVolumeSectionMapper;
import com.aisay.manga.service.StoryService;
import com.aisay.manga.service.PermissionService;
import com.aisay.manga.utils.AiStoryTaskPublisher;
import com.aisay.manga.utils.LocalFileStorageUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
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

    private static final String STORY_STATUS_SECTION_ASSET_PENDING = "section_asset_pending";

    private static final String STORY_STATUS_SECTION_SCRIPT_PENDING = "section_script_pending";

    private static final String STORY_STATUS_FAILED = "failed";

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final StoryVolumeOutlineMapper storyVolumeOutlineMapper;

    private final StoryVolumeSectionMapper storyVolumeSectionMapper;

    private final StoryAssetMapper storyAssetMapper;

    private final StorySectionAssetMapper storySectionAssetMapper;

    private final StorySectionScriptMapper storySectionScriptMapper;

    private final LocalFileStorageUtil localFileStorageUtil;

    private final AiStoryTaskPublisher aiStoryTaskPublisher;

    private final PermissionService permissionService;

    /**
     * 作用：注入故事、角色、分卷大纲数据访问对象，以及 RabbitMQ AI 任务发布器。
     * 调用方：Spring 容器启动时自动构造 StoryServiceImpl。
     */
    public StoryServiceImpl(
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            StoryVolumeOutlineMapper storyVolumeOutlineMapper,
            StoryVolumeSectionMapper storyVolumeSectionMapper,
            StoryAssetMapper storyAssetMapper,
            StorySectionAssetMapper storySectionAssetMapper,
            StorySectionScriptMapper storySectionScriptMapper,
            LocalFileStorageUtil localFileStorageUtil,
            AiStoryTaskPublisher aiStoryTaskPublisher,
            PermissionService permissionService
    ) {
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.storyVolumeOutlineMapper = storyVolumeOutlineMapper;
        this.storyVolumeSectionMapper = storyVolumeSectionMapper;
        this.storyAssetMapper = storyAssetMapper;
        this.storySectionAssetMapper = storySectionAssetMapper;
        this.storySectionScriptMapper = storySectionScriptMapper;
        this.localFileStorageUtil = localFileStorageUtil;
        this.aiStoryTaskPublisher = aiStoryTaskPublisher;
        this.permissionService = permissionService;
    }

    /**
     * 作用：新建一个“生成中”的故事占位记录，保存用户设定的漫剧风格，并把剧情大纲生成任务投递给 Python worker。
     * 调用方：StoryController#generateStory。
     */
    @Override
    @Transactional
    public StoryResponse generateStory(Long userId, StoryGenerateRequest request) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_GENERATE);
        LocalDateTime now = LocalDateTime.now();

        Story story = new Story();
        story.setUserId(userId);
        story.setTitle(resolveText(request.getGenre(), "新故事") + " 剧情大纲生成中");
        story.setGenre(request.getGenre());
        story.setStyle(request.getStyle());
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
        message.setStoryStyle(story.getStyle());
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_REVISE_OUTLINE);
        Story story = getOwnedStory(storyId, userId);
        story.setStatus(STORY_STATUS_REVISING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_STORY_REVISE, userId, storyId);
        message.setGenre(story.getGenre());
        message.setStoryStyle(story.getStyle());
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_UPDATE_DETAIL);
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_GENERATE_VOLUME_OUTLINE);
        Story story = getOwnedStory(storyId, userId);
        if (story.getFullContent() == null || story.getFullContent().isBlank()) {
            throw new IllegalArgumentException("Please generate or fill in the story outline before generating volume outlines.");
        }

        story.setStatus(STORY_STATUS_VOLUME_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        storyVolumeOutlineMapper.delete(new LambdaQueryWrapper<StoryVolumeOutline>().eq(StoryVolumeOutline::getStoryId, storyId));

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_VOLUME_GENERATE, userId, storyId);
        message.setGenre(story.getGenre());
        message.setStoryStyle(story.getStyle());
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_REVISE_VOLUME_OUTLINE);
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
        message.setGenre(story.getGenre());
        message.setStoryStyle(story.getStyle());
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_GENERATE_VOLUME_SECTIONS);
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
        message.setGenre(story.getGenre());
        message.setTitle(story.getTitle());
        message.setStoryStyle(story.getStyle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        message.setVolumeOutlines(List.of(toAiVolumeOutlineItem(volume)));
        publishAfterCommit(message);

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：把指定小节标记为资产生成任务，异步投递给 Python worker。
     * 调用方：StoryController#generateSectionAssets。
     */
    @Override
    @Transactional
    public StoryDetailResponse generateSectionAssets(Long storyId, Long sectionId, Long userId) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_GENERATE_SECTION_ASSETS);
        Story story = getOwnedStory(storyId, userId);
        StoryVolumeSection section = getOwnedSection(storyId, sectionId);
        if (section.getContent() == null || section.getContent().isBlank()) {
            throw new IllegalArgumentException("Section content is required before generating section assets.");
        }

        StoryVolumeOutline volume = getOwnedVolume(storyId, section.getVolumeId());
        story.setStatus(STORY_STATUS_SECTION_ASSET_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_SECTION_ASSET_GENERATE, userId, storyId);
        message.setVolumeId(volume.getId());
        message.setGenre(story.getGenre());
        message.setTitle(story.getTitle());
        message.setStoryStyle(story.getStyle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        message.setVolumeOutlines(List.of(toAiVolumeOutlineItem(volume)));
        message.setSection(toAiVolumeSectionItem(section));
        message.setExistingAssets(getStoryAssetReferences(storyId));
        publishAfterCommit(message);

        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：把指定小节标记为脚本生成任务，异步投递给 Python worker。
     * 调用方：StoryController#generateSectionScript。
     */
    @Override
    @Transactional
    public StoryDetailResponse generateSectionScript(Long storyId, Long sectionId, Long userId) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_GENERATE_SECTION_SCRIPT);
        Story story = getOwnedStory(storyId, userId);
        StoryVolumeSection section = getOwnedSection(storyId, sectionId);
        if (section.getContent() == null || section.getContent().isBlank()) {
            throw new IllegalArgumentException("Section content is required before generating section script.");
        }

        StoryVolumeOutline volume = getOwnedVolume(storyId, section.getVolumeId());
        story.setStatus(STORY_STATUS_SECTION_SCRIPT_PENDING);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        AiStoryTaskMessage message = newTaskMessage(AiRabbitConstants.TASK_SECTION_SCRIPT_GENERATE, userId, storyId);
        message.setVolumeId(volume.getId());
        message.setGenre(story.getGenre());
        message.setTitle(story.getTitle());
        message.setStoryStyle(story.getStyle());
        message.setStorySummary(story.getSynopsis());
        message.setOutline(story.getFullContent());
        message.setMainCharacters(getMainCharacterSettings(storyId));
        message.setVolumeOutlines(List.of(toAiVolumeOutlineItem(volume)));
        message.setSection(toAiVolumeSectionItem(section));
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_UPDATE_VOLUME_OUTLINE);
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
            case AiRabbitConstants.TASK_SECTION_ASSET_GENERATE -> applySectionAssetResult(story, result);
            case AiRabbitConstants.TASK_SECTION_SCRIPT_GENERATE -> applySectionScriptResult(story, result);
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_DETAIL);
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));
        List<StoryVolumeOutline> volumeOutlines = getVolumeOutlineEntities(storyId);
        List<StoryVolumeSection> sectionEntities = getVolumeSectionEntities(storyId);
        Map<Long, List<StoryVolumeSection>> sectionMap = sectionEntities.stream()
                .collect(Collectors.groupingBy(StoryVolumeSection::getVolumeId));
        Map<Long, List<StoryAsset>> sectionAssetMap = getSectionAssetMap(storyId);
        Map<Long, List<StorySectionScript>> sectionScriptMap = getSectionScriptMap(storyId);

        StoryDetailResponse response = new StoryDetailResponse();
        fillStoryResponse(response, story);
        response.setFullContent(story.getFullContent());
        response.setVolumeOutlines(volumeOutlines.stream()
                .map(volume -> toVolumeOutlineItem(volume, sectionMap.getOrDefault(volume.getId(), List.of()), sectionAssetMap, sectionScriptMap))
                .toList());
        response.setCharacters(characters.stream().map(this::toCharacterItem).toList());
        response.setScenes(List.of());
        return response;
    }

    /**
     * 作用：把用户上传的人物音频保存到本地文件系统，并绑定到人物资产。
     * 调用方：StoryController#uploadCharacterAudio。
     */
    @Override
    @Transactional
    public StoryDetailResponse uploadCharacterAudio(Long storyId, Long assetId, Long userId, MultipartFile file) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_UPLOAD_ASSET_AUDIO);
        getOwnedStory(storyId, userId);
        StoryAsset asset = getOwnedStoryAsset(storyId, assetId);
        if (!"CHARACTER".equalsIgnoreCase(asset.getAssetType())) {
            throw new IllegalArgumentException("Only character assets can bind audio.");
        }

        String filePath = localFileStorageUtil.saveFile(file, "character-audio");
        asset.setAudioPath(filePath);
        asset.setUpdatedAt(LocalDateTime.now());
        storyAssetMapper.updateById(asset);
        return getStoryDetail(storyId, userId);
    }

    /**
     * 作用：分页查询当前用户的故事列表。
     * 调用方：StoryController#getUserStories。
     */
    @Override
    public Page<StoryResponse> getUserStories(Long userId, int page, int size) {
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_LIST);
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_UPDATE_BASIC);
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
        permissionService.requireFeature(userId, FeaturePermissionKeys.STORY_DELETE);
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

    private void applySectionAssetResult(Story story, AiStoryTaskResultMessage result) {
        StoryVolumeSectionGenerateResponse response = result.getVolumeSection();
        if (response == null || response.getSections() == null || response.getSections().isEmpty()) {
            markStoryFailed(story);
            return;
        }

        StoryVolumeSectionGenerateResponse.VolumeSectionItem sectionItem = response.getSections().get(0);
        StoryVolumeSection section = storyVolumeSectionMapper.selectOne(new LambdaQueryWrapper<StoryVolumeSection>()
                .eq(StoryVolumeSection::getStoryId, story.getId())
                .eq(StoryVolumeSection::getVolumeId, result.getVolumeId())
                .eq(StoryVolumeSection::getSectionNumber, sectionItem.getSectionNumber())
                .last("LIMIT 1"));
        if (section == null) {
            markStoryFailed(story);
            return;
        }

        storySectionAssetMapper.delete(new LambdaQueryWrapper<StorySectionAsset>()
                .eq(StorySectionAsset::getSectionId, section.getId()));
        applySectionAssets(story.getId(), section.getId(), sectionItem.getAssets());
        story.setStatus(STORY_STATUS_DRAFT);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
    }

    private void applySectionScriptResult(Story story, AiStoryTaskResultMessage result) {
        StorySectionScriptGenerateResponse response = result.getSectionScript();
        if (response == null || response.getShots() == null || response.getShots().isEmpty()) {
            markStoryFailed(story);
            return;
        }

        StoryVolumeSection section = storyVolumeSectionMapper.selectOne(new LambdaQueryWrapper<StoryVolumeSection>()
                .eq(StoryVolumeSection::getStoryId, story.getId())
                .eq(StoryVolumeSection::getVolumeId, result.getVolumeId())
                .eq(StoryVolumeSection::getSectionNumber, response.getSectionNumber())
                .last("LIMIT 1"));
        if (section == null) {
            markStoryFailed(story);
            return;
        }

        storySectionScriptMapper.delete(new LambdaQueryWrapper<StorySectionScript>()
                .eq(StorySectionScript::getSectionId, section.getId()));
        saveSectionScripts(story.getId(), section.getId(), response.getShots());
        story.setStatus(STORY_STATUS_DRAFT);
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

    private List<StoryAssetReference> getStoryAssetReferences(Long storyId) {
        return storyAssetMapper.selectList(new LambdaQueryWrapper<StoryAsset>()
                        .eq(StoryAsset::getStoryId, storyId)
                        .orderByAsc(StoryAsset::getAssetType)
                        .orderByAsc(StoryAsset::getName))
                .stream()
                .map(asset -> new StoryAssetReference(
                        asset.getAssetType(),
                        asset.getName(),
                        asset.getDescription(),
                        asset.getImagePrompt(),
                        asset.getImagePath(),
                        asset.getAudioPath()
                ))
                .toList();
    }

    private Map<Long, List<StoryAsset>> getSectionAssetMap(Long storyId) {
        List<StorySectionAsset> links = storySectionAssetMapper.selectList(new LambdaQueryWrapper<StorySectionAsset>()
                .eq(StorySectionAsset::getStoryId, storyId)
                .orderByAsc(StorySectionAsset::getSectionId)
                .orderByAsc(StorySectionAsset::getId));
        if (links.isEmpty()) {
            return Map.of();
        }

        Map<Long, StoryAsset> assetsById = storyAssetMapper.selectList(new LambdaQueryWrapper<StoryAsset>()
                        .eq(StoryAsset::getStoryId, storyId))
                .stream()
                .collect(Collectors.toMap(StoryAsset::getId, asset -> asset));

        return links.stream()
                .filter(link -> assetsById.containsKey(link.getAssetId()))
                .collect(Collectors.groupingBy(
                        StorySectionAsset::getSectionId,
                        Collectors.mapping(link -> assetsById.get(link.getAssetId()), Collectors.toList())
                ));
    }

    private Map<Long, List<StorySectionScript>> getSectionScriptMap(Long storyId) {
        return storySectionScriptMapper.selectList(new LambdaQueryWrapper<StorySectionScript>()
                        .eq(StorySectionScript::getStoryId, storyId)
                        .orderByAsc(StorySectionScript::getSectionId)
                        .orderByAsc(StorySectionScript::getShotNumber)
                        .orderByAsc(StorySectionScript::getId))
                .stream()
                .collect(Collectors.groupingBy(StorySectionScript::getSectionId));
    }

    private StoryVolumeSection replaceSingleVolumeSection(
            Long storyId,
            Long volumeId,
            StoryVolumeSectionGenerateResponse.VolumeSectionItem item
    ) {
        if (item == null) {
            throw new IllegalArgumentException("Volume section item is required.");
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
        return section;
    }

    private void applySectionAssets(
            Long storyId,
            Long sectionId,
            List<StoryVolumeSectionGenerateResponse.SectionAssetItem> assets
    ) {
        if (assets == null || assets.isEmpty()) {
            return;
        }

        assets.stream()
                .filter(asset -> asset.getName() != null && !asset.getName().isBlank())
                .forEach(asset -> {
                    StoryAsset storyAsset = findOrCreateStoryAsset(storyId, sectionId, asset);
                    linkSectionAsset(storyId, sectionId, storyAsset.getId());
                });
    }

    private StoryAsset findOrCreateStoryAsset(
            Long storyId,
            Long sectionId,
            StoryVolumeSectionGenerateResponse.SectionAssetItem item
    ) {
        String assetType = resolveText(item.getAssetType(), "SCENE").toUpperCase();
        StoryAsset existing = storyAssetMapper.selectOne(new LambdaQueryWrapper<StoryAsset>()
                .eq(StoryAsset::getStoryId, storyId)
                .eq(StoryAsset::getAssetType, assetType)
                .eq(StoryAsset::getName, item.getName())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                existing.setDescription(item.getDescription());
            }
            if (existing.getImagePrompt() == null || existing.getImagePrompt().isBlank()) {
                existing.setImagePrompt(item.getImagePrompt());
            }
            if ((existing.getImagePath() == null || existing.getImagePath().isBlank()) && item.getImagePath() != null) {
                existing.setImagePath(item.getImagePath());
            }
            if (existing.getFirstSectionId() == null && Boolean.TRUE.equals(item.getFirstAppearance())) {
                existing.setFirstSectionId(sectionId);
            }
            existing.setUpdatedAt(now);
            storyAssetMapper.updateById(existing);
            return existing;
        }

        StoryAsset asset = new StoryAsset();
        asset.setStoryId(storyId);
        asset.setAssetType(assetType);
        asset.setName(item.getName());
        asset.setDescription(item.getDescription());
        asset.setImagePrompt(item.getImagePrompt());
        asset.setImagePath(item.getImagePath());
        asset.setAudioPath(item.getAudioPath());
        asset.setFirstSectionId(Boolean.TRUE.equals(item.getFirstAppearance()) ? sectionId : null);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        storyAssetMapper.insert(asset);
        return asset;
    }

    private void linkSectionAsset(Long storyId, Long sectionId, Long assetId) {
        Long existingCount = storySectionAssetMapper.selectCount(new LambdaQueryWrapper<StorySectionAsset>()
                .eq(StorySectionAsset::getSectionId, sectionId)
                .eq(StorySectionAsset::getAssetId, assetId));
        if (existingCount != null && existingCount > 0) {
            return;
        }

        StorySectionAsset link = new StorySectionAsset();
        link.setStoryId(storyId);
        link.setSectionId(sectionId);
        link.setAssetId(assetId);
        link.setCreatedAt(LocalDateTime.now());
        storySectionAssetMapper.insert(link);
    }

    private void saveSectionScripts(
            Long storyId,
            Long sectionId,
            List<StorySectionScriptGenerateResponse.ScriptShotItem> shots
    ) {
        if (shots == null || shots.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<StorySectionScriptGenerateResponse.ScriptShotItem> orderedShots = shots.stream()
                .filter(shot -> shot.getAction() != null && !shot.getAction().isBlank())
                .sorted(Comparator.comparing(
                        StorySectionScriptGenerateResponse.ScriptShotItem::getShotNumber,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        for (int index = 0; index < orderedShots.size(); index++) {
            StorySectionScriptGenerateResponse.ScriptShotItem shot = orderedShots.get(index);
            StorySectionScript script = new StorySectionScript();
            script.setStoryId(storyId);
            script.setSectionId(sectionId);
            script.setShotNumber(index + 1);
            script.setDurationSeconds(shot.getDurationSeconds() == null ? 5 : shot.getDurationSeconds());
            script.setShotType(resolveText(shot.getShotType(), "中景"));
            script.setCameraMovement(shot.getCameraMovement());
            script.setAction(shot.getAction());
            script.setDialogue(shot.getDialogue());
            script.setCreatedAt(now);
            script.setUpdatedAt(now);
            storySectionScriptMapper.insert(script);
        }
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

    private StoryVolumeSection getOwnedSection(Long storyId, Long sectionId) {
        StoryVolumeSection section = storyVolumeSectionMapper.selectById(sectionId);
        if (section == null || !storyId.equals(section.getStoryId())) {
            throw new NoSuchElementException("Volume section not found");
        }
        return section;
    }

    private StoryAsset getOwnedStoryAsset(Long storyId, Long assetId) {
        StoryAsset asset = storyAssetMapper.selectById(assetId);
        if (asset == null || !storyId.equals(asset.getStoryId())) {
            throw new NoSuchElementException("Story asset not found");
        }
        return asset;
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

    private StoryVolumeSectionGenerateResponse.VolumeSectionItem toAiVolumeSectionItem(StoryVolumeSection section) {
        return new StoryVolumeSectionGenerateResponse.VolumeSectionItem(
                section.getSectionNumber(),
                section.getTitle(),
                section.getSummary(),
                section.getContent(),
                section.getEndingHook(),
                List.of()
        );
    }

    private StoryDetailResponse.VolumeOutlineItem toVolumeOutlineItem(
            StoryVolumeOutline volume,
            List<StoryVolumeSection> sections,
            Map<Long, List<StoryAsset>> sectionAssetMap,
            Map<Long, List<StorySectionScript>> sectionScriptMap
    ) {
        return new StoryDetailResponse.VolumeOutlineItem(
                volume.getId(),
                volume.getVolumeNumber(),
                volume.getTitle(),
                volume.getSummary(),
                volume.getContent(),
                volume.getEndingHook(),
                volume.getDetailedContent(),
                sections.stream()
                        .map(section -> toVolumeSectionItem(
                                section,
                                sectionAssetMap.getOrDefault(section.getId(), List.of()),
                                sectionScriptMap.getOrDefault(section.getId(), List.of())
                        ))
                        .toList()
        );
    }

    private StoryDetailResponse.VolumeSectionItem toVolumeSectionItem(
            StoryVolumeSection section,
            List<StoryAsset> assets,
            List<StorySectionScript> scripts
    ) {
        return new StoryDetailResponse.VolumeSectionItem(
                section.getId(),
                section.getSectionNumber(),
                section.getTitle(),
                section.getSummary(),
                section.getContent(),
                section.getEndingHook(),
                assets.stream().map(this::toStoryAssetItem).toList(),
                scripts.stream()
                        .map(StorySectionScript::getDurationSeconds)
                        .filter(duration -> duration != null && duration > 0)
                        .reduce(0, Integer::sum),
                scripts.stream().map(this::toStorySectionScriptItem).toList()
        );
    }

    private StoryDetailResponse.StorySectionScriptItem toStorySectionScriptItem(StorySectionScript script) {
        return new StoryDetailResponse.StorySectionScriptItem(
                script.getId(),
                script.getShotNumber(),
                script.getDurationSeconds(),
                script.getShotType(),
                script.getCameraMovement(),
                script.getAction(),
                script.getDialogue()
        );
    }

    private StoryDetailResponse.StoryAssetItem toStoryAssetItem(StoryAsset asset) {
        return new StoryDetailResponse.StoryAssetItem(
                asset.getId(),
                asset.getAssetType(),
                asset.getName(),
                asset.getDescription(),
                asset.getImagePrompt(),
                asset.getImagePath(),
                toFileUrl(asset.getImagePath()),
                asset.getAudioPath(),
                toFileUrl(asset.getAudioPath()),
                asset.getFirstSectionId()
        );
    }

    private String toFileUrl(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return localFileStorageUtil.getFileUrl(filePath);
    }
}
