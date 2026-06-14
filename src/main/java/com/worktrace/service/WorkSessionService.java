package com.worktrace.service;

import com.worktrace.model.WorkSession;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作会话服务接口。
 * 负责获取指定日期的工作会话数据。
 */
public interface WorkSessionService {

    /** 获取指定日期的全部工作会话，按时间排序。 */
    List<WorkSession> getDailySessions(LocalDate date);

    /** 获取指定日期的工作会话总数。 */
    int getSessionCount(LocalDate date);

    /** 获取指定日期的总工作时长(分钟)。 */
    long getTotalWorkMinutes(LocalDate date);

    /** 获取日期范围内的全部工作会话。 */
    List<WorkSession> getSessionsByDateRange(LocalDate from, LocalDate to);
}
