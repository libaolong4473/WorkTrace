package com.worktrace.collector;

import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;

import java.util.List;

/**
 * 事件聚合器。
 * 将大量原始 FileEvent 聚合为少数有意义的 ActivityBlock。
 *
 * 职责：
 *   - 接收实时 FileEvent 流
 *   - 按时间窗口(默认 5 分钟)和路径分组
 *   - 合并同组事件为一个 ActivityBlock
 *   - 委托 CategoryClassifier 确定活动类别
 *
 * 设计说明：
 *   内部维护一个 pendingEvents 缓冲区，
 *   当事件间隔超过阈值或缓冲区满时触发一次聚合。
 */
public class EventAggregator {

    private static final long GAP_THRESHOLD_MS = 5 * 60 * 1000; // 5 分钟

    private final CategoryClassifier classifier;

    public EventAggregator(CategoryClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * 喂入一条文件事件。当满足聚合条件时返回一个 ActivityBlock，否则返回 null。
     */
    public ActivityBlock onEvent(FileEvent event) {
        // TODO: 实现聚合逻辑
        return null;
    }

    /**
     * 刷新缓冲区，将尚未聚合的事件输出为 ActivityBlock。
     * 通常在应用退出或日期切换时调用。
     */
    public ActivityBlock flush() {
        // TODO: 实现刷新逻辑
        return null;
    }
}
