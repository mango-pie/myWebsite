package com.ai.service.pet.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.pet.PetDeviceMapper;
import com.ai.model.dto.pet.PetDeviceCreateRequest;
import com.ai.model.entity.pet.PetDevice;
import com.ai.model.vo.pet.PetDeviceVO;
import com.ai.service.pet.PetDeviceService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@ConditionalOnModule("pet")
@Service
public class PetDeviceServiceImpl implements PetDeviceService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final int MAX_DEVICES_PER_USER = 20;

    private final SecureRandom secureRandom = new SecureRandom();

    @Resource
    private PetDeviceMapper petDeviceMapper;

    @Override
    public PetDeviceVO bind(PetDeviceCreateRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getDeviceName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "deviceName 不能为空");
        }
        long bound = petDeviceMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE));
        if (bound >= MAX_DEVICES_PER_USER) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "绑定设备数量已达上限，请先解绑");
        }

        String token = generateToken();
        LocalDateTime now = LocalDateTime.now();
        PetDevice device = new PetDevice();
        device.setUserId(userId);
        device.setDeviceName(request.getDeviceName().trim());
        device.setClientInfo(toClientInfoJson(request.getClientInfo()));
        device.setTokenHash(SecureUtil.sha256(token));
        device.setStatus(STATUS_ACTIVE);
        device.setBoundAt(now);
        device.setCreateTime(now);
        device.setUpdateTime(now);
        petDeviceMapper.insert(device);

        PetDeviceVO vo = toVo(device);
        vo.setToken(token);
        return vo;
    }

    @Override
    public List<PetDeviceVO> list(Long userId) {
        List<PetDevice> rows = petDeviceMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("bound_at", false));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public boolean revoke(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        PetDevice device = petDeviceMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", id)
                .eq("user_id", userId));
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "设备不存在");
        }
        if (STATUS_REVOKED.equals(device.getStatus())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        PetDevice patch = new PetDevice();
        patch.setStatus(STATUS_REVOKED);
        patch.setRevokedAt(now);
        patch.setUpdateTime(now);
        return petDeviceMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("id", id)
                .eq("user_id", userId)) > 0;
    }

    /** 256 位随机 token，Base64URL 无填充；库存哈希，明文不落库 */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toClientInfoJson(Object clientInfo) {
        if (clientInfo == null) {
            return null;
        }
        if (clientInfo instanceof String str) {
            return StrUtil.isBlank(str) ? null : str;
        }
        return cn.hutool.json.JSONUtil.toJsonStr(clientInfo);
    }

    private PetDeviceVO toVo(PetDevice device) {
        PetDeviceVO vo = new PetDeviceVO();
        vo.setId(device.getId());
        vo.setDeviceName(device.getDeviceName());
        vo.setClientInfo(parseClientInfo(device.getClientInfo()));
        vo.setStatus(device.getStatus());
        if (device.getBoundAt() != null) {
            vo.setBoundAt(device.getBoundAt().format(TIME_FORMAT));
        }
        if (device.getRevokedAt() != null) {
            vo.setRevokedAt(device.getRevokedAt().format(TIME_FORMAT));
        }
        return vo;
    }

    private Object parseClientInfo(String clientInfoJson) {
        if (StrUtil.isBlank(clientInfoJson)) {
            return null;
        }
        String trimmed = clientInfoJson.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return cn.hutool.json.JSONUtil.parse(trimmed);
            } catch (Exception e) {
                return clientInfoJson;
            }
        }
        return clientInfoJson;
    }
}
