package com.aisay.manga.service.impl;

import com.aisay.manga.dto.ai.StoryOutlineGenerateRequest;
import com.aisay.manga.dto.ai.StoryOutlineGenerateResponse;
import com.aisay.manga.dto.ai.StoryOutlineReviseResponse;
import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryOutlineReviseRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Story;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.ChatSessionMapper;
import com.aisay.manga.repository.StoryMapper;
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

    private static final String SESSION_STATUS_DELETED = "deleted";

    private static final String STORY_STATUS_DRAFT = "draft";

    private static final String OUTLINE_STYLE = "story_outline";

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final ChatSessionMapper chatSessionMapper;

    private final AiEngineClient aiEngineClient;

    public StoryServiceImpl(
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            ChatSessionMapper chatSessionMapper,
            AiEngineClient aiEngineClient
    ) {
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.aiEngineClient = aiEngineClient;
    }

    @Override
    @Transactional
    public StoryResponse generateStory(Long userId, StoryGenerateRequest request) {
        ChatSession session = getOwnedSession(request.getSessionId(), userId);
        StoryOutlineGenerateResponse outline = aiEngineClient.generateStoryOutline(new StoryOutlineGenerateRequest(
                userId,
                session.getId(),
                session.getTitle(),
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

    @Override
    public StoryDetailResponse getStoryDetail(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));

        StoryDetailResponse response = new StoryDetailResponse();
        fillStoryResponse(response, story);
        response.setFullContent(story.getFullContent());
        response.setCharacters(characters.stream().map(this::toCharacterItem).toList());
        response.setScenes(List.of());
        return response;
    }

    @Override
    public Page<StoryResponse> getUserStories(Long userId, int page, int size) {
        Page<Story> storyPage = storyMapper.selectPageByUserId(new Page<>(Math.max(page, 1), Math.max(size, 1)), userId);
        Page<StoryResponse> responsePage = new Page<>(storyPage.getCurrent(), storyPage.getSize(), storyPage.getTotal());
        responsePage.setRecords(storyPage.getRecords().stream().map(this::toStoryResponse).toList());
        return responsePage;
    }

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

    @Override
    @Transactional
    public void deleteStory(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        storyMapper.deleteById(story.getId());
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

    private ChatSession getOwnedSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || SESSION_STATUS_DELETED.equals(session.getStatus())) {
            throw new NoSuchElementException("Session not found");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to access this session");
        }
        return session;
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
}
