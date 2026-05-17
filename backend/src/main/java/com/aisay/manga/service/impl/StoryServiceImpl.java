package com.aisay.manga.service.impl;

import com.aisay.manga.dto.request.StoryGenerateRequest;
import com.aisay.manga.dto.request.StoryUpdateRequest;
import com.aisay.manga.dto.response.StoryDetailResponse;
import com.aisay.manga.dto.response.StoryResponse;
import com.aisay.manga.entity.Character;
import com.aisay.manga.entity.ChatSession;
import com.aisay.manga.entity.Scene;
import com.aisay.manga.entity.Story;
import com.aisay.manga.repository.CharacterMapper;
import com.aisay.manga.repository.ChatSessionMapper;
import com.aisay.manga.repository.SceneMapper;
import com.aisay.manga.repository.StoryMapper;
import com.aisay.manga.service.StoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class StoryServiceImpl implements StoryService {

    private static final String SESSION_STATUS_DELETED = "deleted";

    private static final String STORY_STATUS_DRAFT = "draft";

    private final StoryMapper storyMapper;

    private final CharacterMapper characterMapper;

    private final SceneMapper sceneMapper;

    private final ChatSessionMapper chatSessionMapper;

    public StoryServiceImpl(
            StoryMapper storyMapper,
            CharacterMapper characterMapper,
            SceneMapper sceneMapper,
            ChatSessionMapper chatSessionMapper
    ) {
        this.storyMapper = storyMapper;
        this.characterMapper = characterMapper;
        this.sceneMapper = sceneMapper;
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    @Transactional
    public StoryResponse generateStory(Long userId, StoryGenerateRequest request) {
        getOwnedSession(request.getSessionId(), userId);
        LocalDateTime now = LocalDateTime.now();

        Story story = new Story();
        story.setUserId(userId);
        story.setTitle("示例漫剧");
        story.setGenre("科幻");
        story.setStyle("日漫");
        story.setSynopsis("这是一部由AI助手生成的示例漫剧，讲述少年在霓虹都市中寻找失落星核，并逐步理解勇气与羁绊的故事。");
        story.setFullContent("""
                第一话：坠落的星核
                夜色覆盖新海市，少年林澈在废弃天文台发现一枚会呼吸的蓝色晶体。晶体唤醒了沉睡的机械少女星野，也把他们卷入追逐星核的秘密组织视线中。

                第二话：雨巷中的同盟
                林澈与星野穿过霓虹雨巷，躲避无人机追踪。在最危险的转角，二人决定共同寻找星核来源，并揭开城市上空永不散去的极光真相。
                """);
        story.setStatus(STORY_STATUS_DRAFT);
        story.setViewCount(0);
        story.setLikeCount(0);
        story.setCreatedAt(now);
        story.setUpdatedAt(now);
        storyMapper.insert(story);

        createDefaultCharacters(story.getId());
        createDefaultScenes(story.getId());

        return toStoryResponse(story);
    }

    @Override
    public StoryDetailResponse getStoryDetail(Long storyId, Long userId) {
        Story story = getOwnedStory(storyId, userId);
        List<Character> characters = characterMapper.selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getStoryId, storyId)
                .orderByAsc(Character::getId));
        List<Scene> scenes = sceneMapper.selectList(new LambdaQueryWrapper<Scene>()
                .eq(Scene::getStoryId, storyId)
                .orderByAsc(Scene::getSceneNumber)
                .orderByAsc(Scene::getId));

        StoryDetailResponse response = new StoryDetailResponse();
        fillStoryResponse(response, story);
        response.setFullContent(story.getFullContent());
        response.setCharacters(characters.stream().map(this::toCharacterItem).toList());
        response.setScenes(scenes.stream().map(this::toSceneItem).toList());
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

    private void createDefaultCharacters(Long storyId) {
        Character hero = new Character();
        hero.setStoryId(storyId);
        hero.setName("林澈");
        hero.setRole("主角");
        hero.setDescription("生活在新海市旧城区的高中生，意外获得星核感应能力。");
        hero.setPersonality("外表安静，内心执拗，遇到重要的人会毫不犹豫地向前。");
        hero.setAppearance(Map.<String, Object>of(
                "hair", "黑色短发，发梢带一点蓝光",
                "eyes", "深灰色眼睛",
                "costume", "深色连帽外套与旧款耳机"
        ));
        characterMapper.insert(hero);

        Character partner = new Character();
        partner.setStoryId(storyId);
        partner.setName("星野");
        partner.setRole("搭档");
        partner.setDescription("从星核中苏醒的机械少女，掌握城市极光的部分记忆。");
        partner.setPersonality("理性、直接，偶尔会用非常笨拙的方式表达关心。");
        partner.setAppearance(Map.<String, Object>of(
                "hair", "银白长发",
                "eyes", "蓝紫色电子瞳",
                "costume", "带发光纹路的轻型战斗服"
        ));
        characterMapper.insert(partner);
    }

    private void createDefaultScenes(Long storyId) {
        Scene firstScene = new Scene();
        firstScene.setStoryId(storyId);
        firstScene.setSceneNumber(1);
        firstScene.setSetting("新海市废弃天文台");
        firstScene.setDescription("暴雨夜，主角在布满灰尘的圆顶观测室中发现坠落的星核。");
        firstScene.setVisualElements(Map.<String, Object>of(
                "weather", "暴雨",
                "lighting", "蓝色星核光与闪电交替",
                "mood", "神秘、紧张"
        ));
        sceneMapper.insert(firstScene);

        Scene secondScene = new Scene();
        secondScene.setStoryId(storyId);
        secondScene.setSceneNumber(2);
        secondScene.setSetting("霓虹雨巷");
        secondScene.setDescription("林澈与星野第一次并肩逃离无人机追踪，城市广告牌在雨水中碎成斑斓光影。");
        secondScene.setVisualElements(Map.<String, Object>of(
                "weather", "小雨",
                "lighting", "霓虹反射",
                "mood", "追逐、建立信任"
        ));
        sceneMapper.insert(secondScene);
    }

    private ChatSession getOwnedSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || SESSION_STATUS_DELETED.equals(session.getStatus())) {
            throw new NoSuchElementException("会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return session;
    }

    private Story getOwnedStory(Long storyId, Long userId) {
        Story story = storyMapper.selectById(storyId);
        if (story == null) {
            throw new NoSuchElementException("漫剧不存在");
        }
        if (!story.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该漫剧");
        }
        return story;
    }

    private StoryResponse toStoryResponse(Story story) {
        StoryResponse response = new StoryResponse();
        fillStoryResponse(response, story);
        return response;
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

    private StoryDetailResponse.SceneItem toSceneItem(Scene scene) {
        return new StoryDetailResponse.SceneItem(
                scene.getId(),
                scene.getSceneNumber(),
                scene.getSetting(),
                scene.getDescription(),
                scene.getVisualElements()
        );
    }
}
