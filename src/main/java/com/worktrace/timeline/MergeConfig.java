package com.worktrace.timeline;

/**
 * 聚合配置 —— 控制 ActivityBlockGenerator 的合并行为。
 *
 * @param maxGapMinutes    最大时间间隔(分钟)，超过则强制分裂。默认 15。
 * @param projectPriority  (保留，当前未使用)
 * @param categoryPriority (保留，当前未使用)
 * @param minBlockMinutes  最小活动块持续时间(分钟)，低于此值的噪声块被丢弃。默认 1。
 */
public record MergeConfig(
    int maxGapMinutes,
    boolean projectPriority,
    boolean categoryPriority,
    int minBlockMinutes
) {
    /** 默认配置：15 分钟间隔，最小时长 1 分钟。 */
    public static final MergeConfig DEFAULT = new MergeConfig(15, true, true, 1);

    /** 宽松配置：30 分钟间隔，最小时长 2 分钟。 */
    public static final MergeConfig RELAXED = new MergeConfig(30, true, false, 2);

    /** 严格配置：5 分钟间隔，最小时长 1 分钟。 */
    public static final MergeConfig STRICT  = new MergeConfig(5, false, true, 1);
}
