package com.ai.service.pet.spi;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * diary 模块关闭时的兜底实现；与 PetDiaryBridgeImpl 互斥（diary 开时只注册后者）。
 */
@Component
@ConditionalOnProperty(name = "app.modules.diary", havingValue = "false")
public class NoOpPetDiaryBridge implements PetDiaryBridge {

    @Override
    public DiaryEntryVO getByDate(LocalDate date, Long userId) {
        return null;
    }

    @Override
    public long save(DiaryEntrySaveRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public List<DiaryEntryMonthItemVO> listByMonth(int year, int month, Long userId) {
        return Collections.emptyList();
    }

    private BusinessException disabled() {
        return new BusinessException(ErrorCode.OPERATION_ERROR, "diary 模块未启用");
    }
}
