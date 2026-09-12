package com.ai.service;

import com.ai.model.dto.pet.PetDeviceBindRequest;
import com.ai.model.entity.PetDevice;
import com.ai.model.entity.User;
import com.ai.model.vo.pet.PetDeviceBindVO;
import com.ai.model.vo.pet.PetDeviceVO;
import com.mybatisflex.core.service.IService;
import java.util.List;

public interface PetDeviceService extends IService<PetDevice> {

    PetDeviceBindVO bind(Long userId, PetDeviceBindRequest request);

    List<PetDeviceVO> listByUser(Long userId);

    boolean revoke(Long userId, Long deviceId);

    /**
     * 校验明文 token，返回绑定用户；无效则 null。
     * 成功时会更新 last_used_at。
     */
    User resolveUserByRawToken(String rawToken);
}
