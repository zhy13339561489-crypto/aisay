package com.aisay.manga;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.aisay.manga.repository")
@SpringBootApplication
public class AisayMangaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AisayMangaApplication.class, args);
    }
}
