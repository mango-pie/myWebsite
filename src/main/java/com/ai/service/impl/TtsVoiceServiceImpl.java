package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.config.GptSovitsProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.TtsVoiceProfileMapper;
import com.ai.model.dto.tts.TtsVoiceUpdateRequest;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.TtsProxyService;
import com.ai.service.TtsRefAudioUploadService;
import com.ai.service.TtsVoiceService;
import com.ai.utils.TtsPathUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class TtsVoiceServiceImpl extends ServiceImpl<TtsVoiceProfileMapper, TtsVoiceProfile>
        implements TtsVoiceService {

    private final AtomicBoolean refPreloaded = new AtomicBoolean(false);

    @Resource
    private GptSovitsProperties gptSovitsProperties;

    @Resource
    private TtsProxyService ttsProxyService;

    @Resource
    private TtsRefAudioUploadService ttsRefAudioUploadService;

    @Override
    public boolean isRefPreloaded() {
        return refPreloaded.get();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureSeedVoice() {
        if (!gptSovitsProperties.getSeedVoice().isEnabled()) {
            return;
        }
        long defaultCount = this.count(QueryWrapper.create().where("is_default = ?", 1));
        if (defaultCount > 0) {
            return;
        }
        GptSovitsProperties.SeedVoice seed = gptSovitsProperties.getSeedVoice();
        TtsVoiceProfile profile = new TtsVoiceProfile();
        profile.setName(seed.getName());
        profile.setRefAudioPath(TtsPathUtils.normalizePath(seed.getRefAudioPath()));
        profile.setPromptText(seed.getPromptText());
        profile.setPromptLang(seed.getPromptLang());
        profile.setTextLang(seed.getTextLang());
        profile.setIsDefault(1);
        profile.setStatus(1);
        profile.setSortOrder(0);
        profile.setCreatedTime(LocalDateTime.now());
        profile.setUpdatedTime(LocalDateTime.now());
        this.save(profile);
    }

    @Override
    public List<TtsVoiceVO> listVoices() {
        List<TtsVoiceProfile> list = this.list(QueryWrapper.create()
                .where("status = ?", 1)
                .orderBy("sort_order", true)
                .orderBy("id", true));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public TtsVoiceVO getVoiceVO(Long id) {
        TtsVoiceProfile profile = getByIdOrThrow(id);
        return toVO(profile);
    }

    @Override
    public TtsVoiceProfile getVoiceOrDefault(Long voiceId) {
        if (voiceId != null && voiceId > 0) {
            return getByIdOrThrow(voiceId);
        }
        return getDefaultVoice();
    }

    @Override
    public TtsVoiceProfile getDefaultVoice() {
        TtsVoiceProfile profile = this.getOne(QueryWrapper.create()
                .where("is_default = ?", 1)
                .and("status = ?", 1));
        if (profile == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置默认音色，请先添加或执行 tts_schema.sql");
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addVoice(MultipartFile file, String name, String promptText, String promptLang, String textLang) {
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "音色名称不能为空");
        }
        String path = ttsRefAudioUploadService.saveRefAudio(file, null);

        TtsVoiceProfile profile = new TtsVoiceProfile();
        profile.setName(name.trim());
        profile.setRefAudioPath(path);
        profile.setPromptText(promptText);
        profile.setPromptLang(StrUtil.blankToDefault(promptLang, "zh"));
        profile.setTextLang(StrUtil.blankToDefault(textLang, "zh"));
        profile.setIsDefault(0);
        profile.setStatus(1);
        profile.setSortOrder(0);
        profile.setCreatedTime(LocalDateTime.now());
        profile.setUpdatedTime(LocalDateTime.now());

        long count = this.count(QueryWrapper.create());
        if (count == 0) {
            profile.setIsDefault(1);
        }

        if (!this.save(profile)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建音色失败");
        }
        return profile.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateVoice(TtsVoiceUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        getByIdOrThrow(request.getId());

        TtsVoiceProfile update = new TtsVoiceProfile();
        update.setId(request.getId());
        if (request.getName() != null) {
            update.setName(request.getName().trim());
        }
        if (request.getPromptText() != null) {
            update.setPromptText(request.getPromptText());
        }
        if (request.getPromptLang() != null) {
            update.setPromptLang(request.getPromptLang());
        }
        if (request.getTextLang() != null) {
            update.setTextLang(request.getTextLang());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        update.setUpdatedTime(LocalDateTime.now());
        return this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVoice(Long id) {
        TtsVoiceProfile profile = getByIdOrThrow(id);
        if (profile.getIsDefault() != null && profile.getIsDefault() == 1) {
            long other = this.count(QueryWrapper.create()
                    .where("id <> ?", id)
                    .and("status = ?", 1));
            if (other == 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能删除唯一的默认音色");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先切换其他音色为默认后再删除");
        }
        TtsVoiceProfile update = new TtsVoiceProfile();
        update.setId(id);
        update.setStatus(0);
        update.setUpdatedTime(LocalDateTime.now());
        return this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectDefaultVoice(Long id) {
        TtsVoiceProfile profile = getByIdOrThrow(id);
        if (profile.getStatus() != null && profile.getStatus() == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "音色已禁用");
        }

        List<TtsVoiceProfile> all = this.list();
        LocalDateTime now = LocalDateTime.now();
        for (TtsVoiceProfile p : all) {
            TtsVoiceProfile u = new TtsVoiceProfile();
            u.setId(p.getId());
            u.setIsDefault(p.getId().equals(id) ? 1 : 0);
            u.setUpdatedTime(now);
            this.updateById(u);
        }

        initReferAudio(id);
    }

    @Override
    public void initReferAudio(Long voiceId) {
        TtsVoiceProfile voice = getVoiceOrDefault(voiceId);
        if (!ttsProxyService.isAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GPT-SoVITS 服务不可用");
        }
        ttsProxyService.preloadReferAudio(voice.getRefAudioPath());
        refPreloaded.set(true);
    }

    private TtsVoiceProfile getByIdOrThrow(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TtsVoiceProfile profile = this.getOne(QueryWrapper.create()
                .where("id = ?", id)
                .and("status = ?", 1));
        if (profile == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "音色不存在");
        }
        profile.setRefAudioPath(TtsPathUtils.normalizePath(profile.getRefAudioPath()));
        return profile;
    }

    private TtsVoiceVO toVO(TtsVoiceProfile profile) {
        TtsVoiceVO vo = new TtsVoiceVO();
        BeanUtil.copyProperties(profile, vo);
        return vo;
    }
}
