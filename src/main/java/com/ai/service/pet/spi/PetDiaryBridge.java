package com.ai.service.pet.spi;

import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryVO;

import java.time.LocalDate;
import java.util.List;

/**
 * Pet 日记能力 SPI。由 pet 平台侧定义，diary 模块提供真实实现，
 * diary 关闭时由 {@link NoOpPetDiaryBridge} 兜底，避免 {@code PetFacade} 强依赖 diary 的 Service Bean。
 */
public interface PetDiaryBridge {

    DiaryEntryVO getByDate(LocalDate date, Long userId);

    long save(DiaryEntrySaveRequest request, Long userId);

    List<DiaryEntryMonthItemVO> listByMonth(int year, int month, Long userId);
}
