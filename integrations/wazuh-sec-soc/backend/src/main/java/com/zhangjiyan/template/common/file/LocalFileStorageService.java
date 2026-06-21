package com.zhangjiyan.template.common.file;

import com.zhangjiyan.template.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileStorageProperties properties;
    private final FileValidationProperties validationProperties;

    @Override
    public StoredFileInfo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的文件");
        }
        if (file.getSize() > validationProperties.maxSizeBytes()) {
            throw new BusinessException("文件大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException("文件名包含非法路径字符");
        }
        String extension = FileTypeUtils.extension(originalName);
        if (extension.isBlank() || !validationProperties.allowedExtensions().contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的文件类型: " + extension);
        }

        String dateDir = LocalDate.now().format(DATE_DIR_FORMAT);
        String storedName = UUID.randomUUID() + "." + extension;
        Path baseDir = baseDir();
        Path targetDir = baseDir.resolve(dateDir).normalize();
        Path target = targetDir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) {
            throw new BusinessException("文件存储路径非法");
        }
        try {
            Files.createDirectories(targetDir);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream inputStream = file.getInputStream(); DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                Files.copy(digestInputStream, target);
            }
            String relativePath = baseDir.relativize(target).toString().replace('\\', '/');
            return new StoredFileInfo(originalName, storedName, extension, file.getContentType(), file.getSize(),
                    properties.getStorageType(), relativePath, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("文件保存失败");
        }
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        Path target = resolveStoragePath(storagePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BusinessException("文件不存在或已被清理");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw new BusinessException("文件路径不可访问");
        }
    }

    @Override
    public void delete(String storagePath) {
        Path target = resolveStoragePath(storagePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new BusinessException("文件物理删除失败");
        }
    }

    private Path resolveStoragePath(String storagePath) {
        Path baseDir = baseDir();
        Path target = baseDir.resolve(storagePath == null ? "" : storagePath).normalize();
        if (!target.startsWith(baseDir)) {
            throw new BusinessException("文件存储路径非法");
        }
        return target;
    }

    private Path baseDir() {
        if (properties.getBaseDir() == null || properties.getBaseDir().isBlank()) {
            throw new BusinessException("未配置文件存储目录");
        }
        return Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
    }
}
