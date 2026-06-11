package com.worktrace.timeline;

/**
 * 聚合配置 —— 控制 ActivityBlockGenerator 的合并行为。
 *
 * @param maxGapMinutes    最大时间间隔(分钟)，超过则强制分裂。默认 15。
 * @param projectPriority  是否优先合并同项目文件。默认 true。
 * @param categoryPriority 是否优先合并同类别文件。默认 true。
 */
public record MergeConfig(
    int maxGapMinutes,
    boolean projectPriority,
    boolean categoryPriority
) {
    /** 默认配置：15 分钟间隔，项目优先，类别优先。 */
    public static final MergeConfig DEFAULT = new MergeConfig(15, true, true);

    /** 宽松配置：30 分钟间隔，仅按项目合并。 */
    public static final MergeConfig RELAXED = new MergeConfig(30, true, false);

    /** 严格配置：5 分钟间隔，严格按类别合并。 */
    public static final MergeConfig STRICT  = new MergeConfig(5, false, true);
}
