package com.aisay.manga.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String originalFilename;

    private String filePath;

    private String fileUrl;

    private Long size;

    private String contentType;
}
