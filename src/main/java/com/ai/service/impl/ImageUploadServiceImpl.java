package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.UploadConfig;
import com.ai.constant.SiteSettingConstant;
import com.ai.model.dto.ImageUploadResponse;
import com.ai.model.entity.User;
import com.ai.service.ImageUploadService;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.SiteSettingService;
import com.ai.service.UserService;
import com.ai.utils.ImageUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片上传服务实现
 */
@Service
public class ImageUploadServiceImpl implements ImageUploadService {
    @Autowired
    private UserService userService;
    @Autowired
    private UploadConfig uploadConfig;
    @Autowired
    private SiteSettingService siteSettingService;

    @Autowired
    private IntegrationCredentialsService credentials;

    @Value("${upload.max-file-size:20}")
    private long fallbackMaxFileSize;

    @Override
    public ImageUploadResponse uploadImage(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            return ImageUploadResponse.error("上传文件不能为空");
        }

        long maxFileSize = resolveMaxFileSizeMb();
        if (file.getSize() > maxFileSize * 1024 * 1024) {
            return ImageUploadResponse.error("文件大小不能超过" + maxFileSize + "MB");
        }

        String allowedExt = resolveAllowedImageExt();
        if (!isAllowedImage(file, allowedExt)) {
            return ImageUploadResponse.error("只允许上传" + allowedExt + "格式的图片");
        }
        try {
            String uploadPath = resolveUploadPath();
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath = uploadPath + File.separator;
            }
            String userUploadDir = uploadPath + userId + File.separator;

            String filename = ImageUploadUtil.saveFile(file, userUploadDir);

            String accessPath = uploadConfig.getAccessPath().replace("**", "").replaceAll("/+$", "");
            String accessUrl = resolveUploadBaseUrl() + accessPath + "/" + userId + "/" + filename;

            User user = new User();
            user.setId(userId);
            user.setUserAvatar(accessUrl);

            boolean updateSuccess = userService.updateById(user);
            if (!updateSuccess) {
                File uploadedFile = new File(userUploadDir + filename);
                if (uploadedFile.exists()) {
                    uploadedFile.delete();
                }
                return ImageUploadResponse.error("文件上传成功，但数据库更新失败");
            }

            return ImageUploadResponse.success(accessUrl, filename);
        } catch (IOException e) {
            e.printStackTrace();
            return ImageUploadResponse.error("文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public String uploadCommonImage(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        long maxFileSize = resolveMaxFileSizeMb();
        if (file.getSize() > maxFileSize * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过" + maxFileSize + "MB");
        }

        String allowedExt = resolveAllowedImageExt();
        if (!isAllowedImage(file, allowedExt)) {
            throw new IllegalArgumentException("只允许上传" + allowedExt + "格式的图片");
        }
        try {
            String uploadPath = resolveUploadPath();
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath = uploadPath + File.separator;
            }
            String userUploadDir = uploadPath + userId + File.separator;

            String filename = ImageUploadUtil.saveFile(file, userUploadDir);

            String accessPath = uploadConfig.getAccessPath().replace("**", "").replaceAll("/+$", "");
            return resolveUploadBaseUrl() + accessPath + "/" + userId + "/" + filename;
        } catch (IOException e) {
            throw new IllegalArgumentException("文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public boolean deleteImage(String filename, Long userId) {
        if (filename == null || filename.trim().isEmpty() || userId == null) {
            return false;
        }

        try {
            Path target = ImageUploadUtil.resolveSafeUserFile(Paths.get(resolveUploadPath()), userId, filename);
            if (target == null) {
                return false;
            }
            File file = target.toFile();
            return file.isFile() && file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolveUploadPath() {
        return credentials.uploadPath();
    }

    private String resolveUploadBaseUrl() {
        return credentials.uploadBaseUrl();
    }

    private long resolveMaxFileSizeMb() {
        return siteSettingService.getInt(
                SiteSettingConstant.MODULE_UPLOAD, "max_file_size_mb", (int) fallbackMaxFileSize);
    }

    private String resolveAllowedImageExt() {
        return siteSettingService.getString(
                SiteSettingConstant.MODULE_UPLOAD, "allowed_image_ext", "jpg,jpeg,png,gif,webp");
    }

    private boolean isAllowedImage(MultipartFile file, String allowedExtCsv) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }
        String extension = ImageUploadUtil.getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        Set<String> allowed = Arrays.stream(StrUtil.blankToDefault(allowedExtCsv, "").split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(s -> s.toLowerCase(Locale.ROOT).replace(".", ""))
                .collect(Collectors.toSet());
        return allowed.contains(extension);
    }
}
