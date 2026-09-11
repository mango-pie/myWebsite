package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.mapper.study.StudyHabitCheckLogMapper;
import com.ai.model.entity.StudyHabitCheckLog;
import com.ai.service.StudyHabitCheckLogService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@ConditionalOnModule("study")
@Service
public class StudyHabitCheckLogServiceImpl extends ServiceImpl<StudyHabitCheckLogMapper, StudyHabitCheckLog>
        implements StudyHabitCheckLogService {
}
