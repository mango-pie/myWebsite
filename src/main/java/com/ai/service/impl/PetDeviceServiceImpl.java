package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.mapper.platform.PetDeviceMapper;
import com.ai.model.dto.pet.PetDeviceBindRequest;
import com.ai.model.entity.PetDevice;
import com.ai.model.entity.User;
import com.ai.model.vo.pet.PetDeviceBindVO;
import com.ai.model.vo.pet.PetDeviceVO;
import com.ai.service.PetDeviceService;
import com.ai.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ai.constant.PetAuthConstant.TOKEN_PREFIX;

@Service
public class PetDeviceServiceImpl extends ServiceImpl<PetDeviceMapper, PetDevice> implements PetDeviceService {

    private static final int MAX_DEVICES_PER_USER = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetDeviceBindVO bind(Long userId, PetDeviceBindRequest request) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);
        long active = this.count(QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("revoked_at"));
        ThrowUtils.throwIf(active >= MAX_DEVICES_PER_USER, ErrorCode.OPERATION_ERROR,
                "设备数量已达上限（" + MAX_DEVICES_PER_USER + "）");

        String rawToken = generateRawToken();
        String hash = DigestUtil.sha256Hex(rawToken);
        LocalDateTime now = LocalDateTime.now();
        String name = StrUtil.blankToDefault(request != null ? request.getDeviceName() : null, "EchoBot").trim();
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        String clientInfo = null;
        if (request != null && StrUtil.isNotBlank(request.getClientInfo())) {
            clientInfo = StrUtil.trim(request.getClientInfo());
            if (clientInfo.length() > 255) {
                clientInfo = clientInfo.substring(0, 255);
            }
        }

        PetDevice device = PetDevice.builder()
                .userId(userId)
                .tokenHash(hash)
                .tokenPrefix(rawToken.substring(0, Math.min(12, rawToken.length())))
                .deviceName(name)
                .clientInfo(clientInfo)
                .createdTime(now)
                .updatedTime(now)
                .build();
        boolean ok = this.save(device);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR, "绑定设备失败");

        PetDeviceBindVO vo = new PetDeviceBindVO();
        vo.setId(device.getId());
        vo.setDeviceName(device.getDeviceName());
        vo.setToken(rawToken);
        vo.setTokenPrefix(device.getTokenPrefix());
        return vo;
    }

    @Override
    public List<PetDeviceVO> listByUser(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);
        List<PetDevice> list = this.list(QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("created_time", false));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revoke(Long userId, Long deviceId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(deviceId == null || deviceId <= 0, ErrorCode.PARAMS_ERROR);
        PetDevice device = this.getById(deviceId);
        ThrowUtils.throwIf(device == null || !userId.equals(device.getUserId()), ErrorCode.NOT_FOUND_ERROR, "设备不存在");
        if (device.getRevokedAt() != null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        device.setRevokedAt(now);
        device.setUpdatedTime(now);
        return this.updateById(device);
    }

    @Override
    public User resolveUserByRawToken(String rawToken) {
        if (StrUtil.isBlank(rawToken) || !rawToken.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String hash = DigestUtil.sha256Hex(rawToken.trim());
        PetDevice device = this.getOne(QueryWrapper.create()
                .eq("token_hash", hash)
                .isNull("revoked_at")
                .limit(1));
        if (device == null || device.getUserId() == null) {
            return null;
        }
        User user = userService.getById(device.getUserId());
        if (user == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        device.setLastUsedAt(now);
        device.setUpdatedTime(now);
        this.updateById(device);
        return user;
    }

    private PetDeviceVO toVO(PetDevice d) {
        PetDeviceVO vo = new PetDeviceVO();
        vo.setId(d.getId());
        vo.setDeviceName(d.getDeviceName());
        vo.setClientInfo(d.getClientInfo());
        vo.setTokenPrefix(d.getTokenPrefix());
        vo.setLastUsedAt(d.getLastUsedAt());
        vo.setCreatedTime(d.getCreatedTime());
        vo.setRevoked(d.getRevokedAt() != null);
        return vo;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return TOKEN_PREFIX + body;
    }
}
