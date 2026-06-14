package com.worktrace.service;

import com.worktrace.model.ActivityBlock;

import java.time.LocalDate;
import java.util.List;

/**
 * 时间线服务接口。
 * 负责将 ActivityBlock 按时间维度组织为可展示的时间线数据。
 *
 * 职责：
 *   - 获取指定日期的活动块列表
 *   - 按小时 / 半天 / 全天维度聚合
 *   - 计算各活动类别的时长占比
 *
 * 扩展点：
 *   后续可在此接口上叠加 AI 日报生成能力。
 */
public interface TimelineService {

    /** 获取指定日期的全部活动块，按时间排序。 */
    List<ActivityBlock> getDailyTimeline(LocalDate date);

    /** 获取指定日期各活动类别的总时长(分钟)。 */
    java.util.Map<String, Long> getCategoryDuration(LocalDate date);

    /** 获取指定日期的总活动时长(分钟)。 */
    long getTotalActiveMinutes(LocalDate date);

    /** 获取日期范围内各活动类别的总时长(分钟)。 */
    java.util.Map<String, Long> getCategoryDurationByRange(LocalDate from, LocalDate to);
}
