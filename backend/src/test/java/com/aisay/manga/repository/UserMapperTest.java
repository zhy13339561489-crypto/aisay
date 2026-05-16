package com.aisay.manga.repository;

import com.aisay.manga.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfSystemProperty(named = "aisay.integration.mysql", matches = "true")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAndSelectUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        User user = new User();
        user.setUsername("mapper_" + suffix);
        user.setEmail("mapper_" + suffix + "@test.local");
        user.setPasswordHash("bcrypt-placeholder");
        user.setAvatarPath("/avatars/default.png");
        user.setPreferences(Map.of("theme", "light", "language", "zh-CN"));

        int inserted = userMapper.insert(user);
        assertEquals(1, inserted);
        assertNotNull(user.getId());

        User selected = userMapper.selectById(user.getId());
        assertNotNull(selected);
        assertEquals(user.getUsername(), selected.getUsername());
        assertEquals(user.getEmail(), selected.getEmail());
        assertEquals("zh-CN", selected.getPreferences().get("language"));

        userMapper.deleteById(user.getId());
    }
}
