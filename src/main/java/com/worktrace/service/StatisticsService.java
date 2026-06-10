package com.worktrace.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 统计服务接口。
 * 提供各维度的数据聚合，供今日概览和项目统计面板使用。
 *
 * 职责：
 *   - 按文件扩展名统计事件数量
 *   - 按项目统计活动时长
 *   - 按小时统计事件分布(热力图数据)
 */
public interface StatisticsService {

    /** 获取指定日期各扩展名的事件数量。 */
    Map<String, Long> countByExtension(LocalDate date);

    /** 获取指定日期各项目的活动时长(分钟)。 */
    Map<String, Long> durationByProject(LocalDate date);

    /** 获取指定日期每小时的事件数量(24 项)。 */
    Map<Integer, Long> hourlyDistribution(LocalDate date);

    /** 获取指定日期的总事件数。 */
    long totalEventCount(LocalDate date);
}
