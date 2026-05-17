package com.aisay.manga.config;

import com.aisay.manga.utils.LocalFileStorageUtil;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class StorageConfig {

    @Value("${storage.root-path:./storage}")
    private String rootPath;

    @Value("${storage.max-file-size:10MB}")
    private String maxFileSize;

    @Value("${storage.max-request-size:50MB}")
    private String maxRequestSize;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse(maxFileSize));
        factory.setMaxRequestSize(DataSize.parse(maxRequestSize));
        return factory.createMultipartConfig();
    }

    @Bean
    public LocalFileStorageUtil localFileStorageUtil() {
        return new LocalFileStorageUtil(rootPath, DataSize.parse(maxFileSize).toBytes());
    }
}
