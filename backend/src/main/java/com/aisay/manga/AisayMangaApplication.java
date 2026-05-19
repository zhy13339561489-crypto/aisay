package com.aisay.manga;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.aisay.manga.repository")
@SpringBootApplication
public class AisayMangaApplication {

    /**
     * 作用：启动 Spring Boot 后端服务。
     * 调用方：本地运行或部署启动 com.aisay.manga.AisayMangaApplication 时由 JVM 调用。
     */
    public static void main(String[] args) {
        SpringApplication.run(AisayMangaApplication.class, args);
    }
}
