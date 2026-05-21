package com.aisay.manga.controller;

import com.aisay.manga.dto.response.ApiResponse;
import com.aisay.manga.dto.response.FileUploadResponse;
import com.aisay.manga.utils.LocalFileStorageUtil;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final LocalFileStorageUtil fileStorageUtil;

    /**
     * 作用：注入本地文件存储工具。
     * 调用方：Spring 容器启动时自动构造 FileController。
     */
    public FileController(LocalFileStorageUtil fileStorageUtil) {
        this.fileStorageUtil = fileStorageUtil;
    }

    /**
     * 作用：上传文件到本地存储目录并返回可访问路径。
     * 调用方：前端上传头像、封面或资源文件时请求 POST /api/files/upload。
     */
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "resources") String category
    ) {
        String filePath = fileStorageUtil.saveFile(file, category);
        FileUploadResponse response = new FileUploadResponse(
                file.getOriginalFilename(),
                filePath,
                fileStorageUtil.getFileUrl(filePath),
                file.getSize(),
                file.getContentType()
        );
        return ApiResponse.success("文件上传成功", response);
    }

    /**
     * 作用：按分类、日期和文件名读取本地文件资源。
     * 调用方：浏览器访问文件 URL /api/files/{category}/{date}/{filename}。
     */
    @GetMapping("/{category}/{date}/{filename:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String category,
            @PathVariable String date,
            @PathVariable String filename
    ) {
        String filePath = fileStorageUtil.buildRelativePath(category, date, filename);
        Resource resource = fileStorageUtil.loadFile(filePath);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * 作用：删除指定本地文件。
     * 调用方：前端资源删除操作请求 DELETE /api/files/{category}/{date}/{filename}。
     */
    @DeleteMapping("/{category}/{date}/{filename:.+}")
    public ApiResponse<Void> deleteFile(
            @PathVariable String category,
            @PathVariable String date,
            @PathVariable String filename
    ) {
        String filePath = fileStorageUtil.buildRelativePath(category, date, filename);
        fileStorageUtil.deleteFile(filePath);
        return ApiResponse.success("文件删除成功", null);
    }

    private MediaType resolveMediaType(String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lowerName.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (lowerName.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (lowerName.endsWith(".m4a")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (lowerName.endsWith(".ogg")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (lowerName.endsWith(".flac")) {
            return MediaType.parseMediaType("audio/flac");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
