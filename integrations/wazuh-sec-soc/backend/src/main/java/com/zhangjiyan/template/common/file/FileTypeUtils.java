package com.zhangjiyan.template.common.file;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class FileTypeUtils {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private FileTypeUtils() {
    }

    public static String extension(String filename) {
        String cleanName = StringUtils.cleanPath(filename == null ? "" : filename);
        int dotIndex = cleanName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanName.length() - 1) {
            return "";
        }
        return cleanName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(String extension, String contentType) {
        return IMAGE_EXTENSIONS.contains(extension) || (contentType != null && contentType.startsWith("image/"));
    }
}
