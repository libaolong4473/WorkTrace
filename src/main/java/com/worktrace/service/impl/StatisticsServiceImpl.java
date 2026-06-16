package com.worktrace.service.impl;

import com.worktrace.database.ActivityRepository;
import com.worktrace.database.FileEventRepository;
import com.worktrace.database.ProjectRepository;
import com.worktrace.service.StatisticsService;
import com.worktrace.util.LogUtil;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StatisticsService 实现。
 * 提供按类别、按项目、按时间段的统计数据。
 */
public class StatisticsServiceImpl implements StatisticsService {

    private final ActivityRepository activityRepo;
    private final FileEventRepository fileEventRepo;
    private final ProjectRepository projectRepo;

    public StatisticsServiceImpl(ActivityRepository activityRepo,
                                 FileEventRepository fileEventRepo,
                                 ProjectRepository projectRepo) {
        this.activityRepo  = activityRepo;
        this.fileEventRepo = fileEventRepo;
        this.projectRepo   = projectRepo;
    }

    @Override
    public Map<String, Long> countByExtension(LocalDate date) {
        try {
            var counts = fileEventRepo.countGroupByExtension(date);
            Map<String, Long> result = new LinkedHashMap<>();
            for (var c : counts) {
                result.put(c.key(), c.count());
            }
            return result;
        } catch (Exception e) {
            LogUtil.error("统计扩展名失败: " + e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<String, Long> durationByProject(LocalDate date) {
        try {
            var stats = projectRepo.getProjectStats(date);
            Map<String, Long> result = new LinkedHashMap<>();
            for (var s : stats) {
                result.put(s.projectName(), s.totalMinutes());
            }
            return result;
        } catch (Exception e) {
            LogUtil.error("统计项目时长失败: " + e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<Integer, Long> hourlyDistribution(LocalDate date) {
        // 基于 activity_block 的 start_time 按小时分组
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) result.put(h, 0L);

        try {
            List<com.worktrace.model.ActivityBlock> blocks = activityRepo.findByDate(date);
            for (var block : blocks) {
                int hour = block.getStartTime().getHour();
                long minutes = java.time.Duration.between(block.getStartTime(), block.getEndTime()).toMinutes();
                result.merge(hour, Math.max(1, minutes), Long::sum);
            }
        } catch (Exception e) {
            LogUtil.error("统计小时分布失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public long totalEventCount(LocalDate date) {
        try {
            return fileEventRepo.countByDate(date);
        } catch (Exception e) {
            LogUtil.error("统计事件总数失败: " + e.getMessage());
            return 0;
        }
    }
}
