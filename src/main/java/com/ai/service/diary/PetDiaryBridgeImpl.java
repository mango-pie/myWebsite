package com.ai.service.diary;

import com.ai.config.ConditionalOnModule;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.service.DiaryEntryService;
import com.ai.service.pet.spi.PetDiaryBridge;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * diary 模块提供的 {@link PetDiaryBridge} 真实实现，仅在 diary 模块启用时注册。
 */
@ConditionalOnModule("diary")
@Component
public class PetDiaryBridgeImpl implements PetDiaryBridge {

    @Resource
    private DiaryEntryService diaryEntryService;

    @Override
    public DiaryEntryVO getByDate(LocalDate date, Long userId) {
        return diaryEntryService.getByDate(date, userId);
    }

    @Override
    public long save(DiaryEntrySaveRequest request, Long userId) {
        return diaryEntryService.saveDiaryEntry(request, userId);
    }

    @Override
    public List<DiaryEntryMonthItemVO> listByMonth(int year, int month, Long userId) {
        return diaryEntryService.listByMonth(year, month, userId);
    }
}
