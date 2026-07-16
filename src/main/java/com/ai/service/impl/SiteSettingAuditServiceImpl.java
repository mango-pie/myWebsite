package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.mapper.SiteSettingAuditMapper;
import com.ai.model.entity.SiteSettingAudit;
import com.ai.model.vo.setting.SiteSettingAuditVO;
import com.ai.service.SiteSettingAuditService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class SiteSettingAuditServiceImpl implements SiteSettingAuditService {

    @Resource
    private SiteSettingAuditMapper siteSettingAuditMapper;

    @Override
    public void record(String module, String settingKey, String oldValue, String newValue,
                       Long operatorId, String action) {
        SiteSettingAudit row = SiteSettingAudit.builder()
                .module(module)
                .settingKey(settingKey)
                .oldValue(oldValue)
                .newValue(newValue)
                .operatorId(operatorId)
                .action(action)
                .createTime(LocalDateTime.now())
                .build();
        siteSettingAuditMapper.insert(row);
    }

    @Override
    public Page<SiteSettingAuditVO> page(String module, int pageNum, int pageSize) {
        int num = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryWrapper query = QueryWrapper.create().orderBy("create_time", false);
        if (StrUtil.isNotBlank(module)) {
            query.eq("module", module.trim());
        }
        Page<SiteSettingAudit> page = siteSettingAuditMapper.paginate(Page.of(num, size), query);
        Page<SiteSettingAuditVO> voPage = new Page<>(num, size, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private SiteSettingAuditVO toVO(SiteSettingAudit row) {
        return SiteSettingAuditVO.builder()
                .id(row.getId())
                .module(row.getModule())
                .settingKey(row.getSettingKey())
                .oldValue(row.getOldValue())
                .newValue(row.getNewValue())
                .operatorId(row.getOperatorId())
                .action(row.getAction())
                .createTime(row.getCreateTime())
                .build();
    }
}
