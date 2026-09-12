package com.ai.service.impl;

import com.ai.config.UploadConfig;
import com.ai.model.dto.ImageUploadResponse;
import com.ai.model.entity.User;
import com.ai.service.ImageUploadService;
import com.ai.service.UserService;
import com.ai.utils.ImageUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * 图片上传服务实现
 */
@Service
public class ImageUploadServiceImpl implements ImageUploadService {
    @Autowired
    private UserService userService;
    @Autowired
    private UploadConfig uploadConfig;

    // 最大文件大小(MB)
    @Value("${upload.max-file-size:5}")
    private long maxFileSize;

    // 应用访问基础URL
    @Value("${upload.base-url:http://localhost:8123/api}")
    private String baseUrl;

    @Override
    public ImageUploadResponse uploadImage(MultipartFile file, Long userId) {
        // 验证文件是否为空
        if (file.isEmpty()) {
            return ImageUploadResponse.error("上传文件不能为空");
        }

        // 验证文件大小
        if (file.getSize() > maxFileSize * 1024 * 1024) {
            return ImageUploadResponse.error("文件大小不能超过" + maxFileSize + "MB");
        }

        // 验证文件类型
        if (!ImageUploadUtil.isImageFile(file)) {
            return ImageUploadResponse.error("只允许上传jpg、jpeg、png、gif、bmp格式的图片");
        }
        System.out.println(123);
        try {
            // 1. 创建用户专属目录（使用配置路径）
            String uploadPath = uploadConfig.getUploadPath();
            // 确保路径以文件分隔符结尾
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath = uploadPath + File.separator;
            }
            String userUploadDir = uploadPath + userId + File.separator;

            // 2. 保存文件
            String filename = ImageUploadUtil.saveFile(file, userUploadDir);

            // 3. 构建访问URL
            String accessPath = uploadConfig.getAccessPath().replace("**", "").replaceAll("/+$", "");
            String accessUrl = baseUrl + accessPath + "/" + userId + "/" + filename;

            System.out.println(accessUrl);
            // 4. 将URL保存到数据库（更新用户头像）
            User user = new User();
            user.setId(userId);
            user.setUserAvatar(accessUrl);





            boolean updateSuccess = userService.updateById(user);
            if (!updateSuccess) {
                // 若数据库更新失败，删除已上传文件
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

        // 验证文件大小
        if (file.getSize() > maxFileSize * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过" + maxFileSize + "MB");
        }

        // 验证文件类型
        if (!ImageUploadUtil.isImageFile(file)) {
            throw new IllegalArgumentException("只允许上传jpg、jpeg、png、gif、bmp格式的图片");
        }
        System.out.println(123);
        try {
            // 1. 创建用户专属目录（使用配置路径）
            String uploadPath = uploadConfig.getUploadPath();
            // 确保路径以文件分隔符结尾
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath = uploadPath + File.separator;
            }
            String userUploadDir = uploadPath + userId + File.separator;

            // 2. 保存文件
            String filename = ImageUploadUtil.saveFile(file, userUploadDir);

            // 3. 构建访问URL
            String accessPath = uploadConfig.getAccessPath().replace("**", "").replaceAll("/+$", "");
            String accessUrl = baseUrl + accessPath + "/" + userId + "/" + filename;

            System.out.println(accessUrl);
            return accessUrl;
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
            String filePath = uploadConfig.getUploadPath() + userId + "/" + filename;
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                return file.delete();
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}