package com.worktrace.service.impl;

import com.worktrace.database.ActivityRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.service.TimelineService;
import com.worktrace.util.LogUtil;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * TimelineService 的标准实现。
 *
 * 职责：
 *   - 查询指定日期的活动块列表
 *   - 统计各类别时长分布
 *   - 计算总活动时长
 *
 * 设计约束：
 *   - 所有数据库异常在此类内部捕获并降级，不向上抛出
 *   - Controller 层禁止直接访问 Repository
 *   - 通过构造函数注入 ActivityRepository
 *
 * 调用关系：
 *   MainController → TimelineService(接口) → TimelineServiceImpl → ActivityRepository → SQLite
 */
public class TimelineServiceImpl implements TimelineService {

    private final ActivityRepository activityRepository;

    /**
     * @param activityRepository 活动块仓库(由 App 层创建并注入)
     */
    public TimelineServiceImpl(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 获取指定日期的全部活动块，按 startTime 升序。
     *
     * @param date 目标日期
     * @return 活动块列表，查询失败时返回空列表
     */
    @Override
    public List<ActivityBlock> getDailyTimeline(LocalDate date) {
        try {
            List<ActivityBlock> blocks = activityRepository.findByDate(date);
            LogUtil.info("查询时间线: " + date + " → " + blocks.size() + " 个活动块");
            return blocks;
        } catch (Exception e) {
            LogUtil.error("查询时间线失败: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 统计指定日期各类别的总活动时长(分钟)。
     *
     * 返回的 Map 保证包含所有 6 个类别键，缺失的类别值为 0。
     * 这样 UI 层可以直接遍历而无需做 null 检查。
     *
     * 示例返回值：
     *   {CODE=120, DOCUMENT=30, IMAGE=0, VIDEO=0, CONFIG=15, OTHER=5}
     *
     * @param date 目标日期
     * @return 类别 → 时长(分钟) 的映射，查询失败时返回全零 Map
     */
    @Override
    public Map<String, Long> getCategoryDuration(LocalDate date) {
        // 初始化所有类别为 0
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (Category cat : Category.values()) {
            result.put(cat.key, 0L);
        }

        try {
            List<ActivityRepository.CategoryDuration> durations =
                activityRepository.durationByCategory(date);

            for (ActivityRepository.CategoryDuration cd : durations) {
                result.merge(cd.category(), cd.minutes(), Long::sum);
            }
        } catch (Exception e) {
            LogUtil.error("统计类别时长失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取指定日期的总活动时长(分钟)。
     *
     * 计算方式：所有 ActivityBlock 的 (endTime - startTime) 之和。
     * 重叠时间段会被重复计算(这是预期行为，因为聚合后不应重叠)。
     *
     * @param date 目标日期
     * @return 总分钟数，查询失败时返回 0
     */
    @Override
    public long getTotalActiveMinutes(LocalDate date) {
        try {
            long total = activityRepository.totalDurationByDate(date);
            LogUtil.info("总活动时长: " + date + " → " + total + " 分钟");
            return total;
        } catch (Exception e) {
            LogUtil.error("查询总活动时长失败: " + e.getMessage());
            return 0;
        }
    }

    // ==================== 内部常量 ====================

    /**
     * 活动类别枚举 —— 与 CategoryClassifier 和数据库 category 字段保持一致。
     * 用于确保 getCategoryDuration 返回的 Map 包含所有固定键。
     */
    private enum Category {
        CODE("CODE"),
        DOCUMENT("DOCUMENT"),
        IMAGE("IMAGE"),
        VIDEO("VIDEO"),
        CONFIG("CONFIG"),
        OTHER("OTHER");

        final String key;
        Category(String key) { this.key = key; }
    }
}
