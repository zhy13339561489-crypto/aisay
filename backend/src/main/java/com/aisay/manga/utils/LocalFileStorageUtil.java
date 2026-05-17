package com.aisay.manga.utils;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class LocalFileStorageUtil {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]+$");

    private static final Pattern DATE_SEGMENT = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "pdf", "epub");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp",
            "application/pdf",
            "application/epub+zip",
            "application/octet-stream"
    );

    private final Path rootPath;

    private final long maxFileSizeBytes;

    public LocalFileStorageUtil(String rootPath, long maxFileSizeBytes) {
        this.rootPath = Paths.get(rootPath).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        createDirectories();
    }

    public String saveFile(MultipartFile file, String category) {
        String safeCategory = validateCategory(category);
        validateFile(file);

        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;
        String date = LocalDate.now().toString();
        Path targetDirectory = resolveSafePath(safeCategory, date);
        Path targetFile = targetDirectory.resolve(fileName).normalize();
        ensureInsideRoot(targetFile);

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new UncheckedIOException("保存文件失败", e);
        }

        return toRelativePath(safeCategory, date, fileName);
    }

    public Resource loadFile(String filePath) {
        Path file = resolveRelativeFile(filePath);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new IllegalArgumentException("文件不存在或不可读");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("文件路径无效", e);
        }
    }

    public boolean deleteFile(String filePath) {
        Path file = resolveRelativeFile(filePath);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("删除文件失败", e);
        }
    }

    public String getFileUrl(String filePath) {
        return "/api/files/" + normalizeRelativePath(filePath);
    }

    public String buildRelativePath(String category, String date, String filename) {
        String safeCategory = validateCategory(category);
        validateDate(date);
        validateFilename(filename);
        return toRelativePath(safeCategory, date, filename);
    }

    private void createDirectories() {
        try {
            Files.createDirectories(rootPath);
            Files.createDirectories(rootPath.resolve("avatars"));
            Files.createDirectories(rootPath.resolve("resources"));
            Files.createDirectories(rootPath.resolve("covers"));
        } catch (IOException e) {
            throw new UncheckedIOException("创建存储目录失败", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("文件大小不能超过 " + maxFileSizeBytes + " 字节");
        }

        String originalFilename = file.getOriginalFilename();
        validateFilename(originalFilename);
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不支持的文件内容类型: " + contentType);
        }
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("文件分类不能为空");
        }
        String normalized = category.trim();
        if (!SAFE_SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("文件分类只能包含字母、数字、下划线和短横线");
        }
        return normalized;
    }

    private void validateDate(String date) {
        if (date == null || !DATE_SEGMENT.matcher(date).matches()) {
            throw new IllegalArgumentException("文件日期路径无效");
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("文件名不能包含路径字符");
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件必须包含扩展名");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Path resolveRelativeFile(String filePath) {
        String normalizedPath = normalizeRelativePath(filePath);
        Path file = rootPath.resolve(normalizedPath).normalize();
        ensureInsideRoot(file);
        return file;
    }

    private Path resolveSafePath(String category, String date) {
        Path path = rootPath.resolve(category).resolve(date).normalize();
        ensureInsideRoot(path);
        return path;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.normalize().startsWith(rootPath)) {
            throw new IllegalArgumentException("文件路径越界");
        }
    }

    private String normalizeRelativePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String normalized = filePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("文件路径无效");
        }
        return normalized;
    }

    private String toRelativePath(String category, String date, String filename) {
        return category + "/" + date + "/" + filename;
    }
}
