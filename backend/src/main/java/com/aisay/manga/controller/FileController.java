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

    public FileController(LocalFileStorageUtil fileStorageUtil) {
        this.fileStorageUtil = fileStorageUtil;
    }

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

    @GetMapping("/{category}/{date}/{filename:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String category,
            @PathVariable String date,
            @PathVariable String filename
    ) {
        String filePath = fileStorageUtil.buildRelativePath(category, date, filename);
        Resource resource = fileStorageUtil.loadFile(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

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
}
