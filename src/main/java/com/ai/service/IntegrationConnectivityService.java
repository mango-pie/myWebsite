package com.ai.service;

import com.ai.model.vo.setting.IntegrationTestResultVO;

import java.util.List;

public interface IntegrationConnectivityService {

    IntegrationTestResultVO test(String target);

    /** 探测全部已知依赖目标（顺序固定，供健康面板）。 */
    List<IntegrationTestResultVO> testAll();
}
