package com.ai.service.pet;

import com.ai.model.dto.pet.PetDeviceCreateRequest;
import com.ai.model.vo.pet.PetDeviceVO;

import java.util.List;

public interface PetDeviceService {

    /** 绑定新设备，明文 token 仅此一次随 VO 返回 */
    PetDeviceVO bind(PetDeviceCreateRequest request, Long userId);

    List<PetDeviceVO> list(Long userId);

    boolean revoke(Long id, Long userId);
}
