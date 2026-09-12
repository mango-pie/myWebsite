package com.ai.service.impl;

import com.ai.mapper.StudyHabitCheckLogMapper;
import com.ai.model.entity.StudyHabitCheckLog;
import com.ai.service.StudyHabitCheckLogService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class StudyHabitCheckLogServiceImpl extends ServiceImpl<StudyHabitCheckLogMapper, StudyHabitCheckLog>
        implements StudyHabitCheckLogService {
}
