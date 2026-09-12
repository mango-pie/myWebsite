package com.ai.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * 图片上传工具类
 */
public class ImageUploadUtil {

    // 允许上传的图片格式
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp"};

    /**
     * 验证文件是否为图片
     */
    public static boolean isImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            return false;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filename) {
        int lastIndex = filename.lastIndexOf(".");
        if (lastIndex == -1) {
            return "";
        }
        return filename.substring(lastIndex);
    }

    /**
     * 生成唯一的文件名
     */
    public static String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        Random random = new Random();
        int randomNum = random.nextInt(1000);
        return timestamp + "_" + randomNum + extension;
    }

    /**
     * 保存文件到指定路径
     */
    public static String saveFile(MultipartFile file, String uploadDir) throws IOException {
        // 创建目录（如果不存在）
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一文件名
        String filename = generateUniqueFilename(file.getOriginalFilename());
        File destFile = new File(uploadDir + filename);

        // 保存文件
        file.transferTo(destFile);

        return filename;
    }

    /**
     * 将客户端传入的 filename 解析到 {uploadRoot}/{userId}/ 下的普通文件路径。
     * 拒绝路径穿越（..、分隔符、空字节）；解析失败返回 null。
     */
    public static Path resolveSafeUserFile(Path uploadRoot, Long userId, String filename) {
        if (uploadRoot == null || userId == null || filename == null) {
            return null;
        }
        String name = filename.trim();
        if (name.isEmpty() || name.indexOf('\0') >= 0) {
            return null;
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return null;
        }
        Path userDir = uploadRoot.toAbsolutePath().normalize().resolve(String.valueOf(userId)).normalize();
        Path target = userDir.resolve(name).normalize();
        if (!target.startsWith(userDir)) {
            return null;
        }
        return target;
    }
}