package com.ai.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.config.GptSovitsProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.TtsRefAudioUploadService;
import com.ai.utils.TtsPathUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TtsRefAudioUploadServiceImpl implements TtsRefAudioUploadService {

    @Resource
    private GptSovitsProperties gptSovitsProperties;

    @Override
    public String saveRefAudio(MultipartFile file, Long voiceId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参考音频文件不能为空");
        }

        GptSovitsProperties.RefAudio config = gptSovitsProperties.getRefAudio();
        long maxBytes = config.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 " + config.getMaxSizeMb() + "MB");
        }

        String original = file.getOriginalFilename();
        String ext = FileUtil.extName(original);
        if (StrUtil.isBlank(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别文件扩展名");
        }
        Set<String> allowed = Arrays.stream(config.getAllowedExt().split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持: " + config.getAllowedExt());
        }

        String dir = config.getUploadDir();
        if (!dir.endsWith("/") && !dir.endsWith("\\")) {
            dir = dir + File.separator;
        }
        String subDir = (voiceId != null ? voiceId : IdUtil.fastSimpleUUID()) + File.separator;
        Path targetDir = Path.of(dir + subDir);
        try {
            Files.createDirectories(targetDir);
            String filename = IdUtil.fastSimpleUUID() + "." + ext;
            Path target = targetDir.resolve(filename);
            file.transferTo(target.toFile());
            return TtsPathUtils.normalizePath(target.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存参考音频失败");
        }
    }
}
