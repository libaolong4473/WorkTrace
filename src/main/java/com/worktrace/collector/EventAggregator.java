package com.worktrace.collector;

import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.timeline.ActivityBlockGenerator;
import com.worktrace.timeline.MergeConfig;
import com.worktrace.util.LogUtil;
import com.worktrace.collector.ProjectDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 事件聚合器 —— 连接采集层与时间线层的桥梁。
 *
 * 完整事件流：
 *   FileWatcherService
 *       → listener.onFileEvent()
 *           → FileEventRepository.insert(event)     // 原始事件持久化
 *           → EventAggregator.accept(event)          // 喂入聚合器
 *               → buffer 攒批
 *               → 达到阈值 → flush()
 *                   → ActivityBlockGenerator.generate(events)
 *                   → onFlush callback → ActivityRepository.batchInsert(blocks)
 *                   → 清空已完成块(释放内存)
 *
 * 线程模型：
 *   FileWatcherService 的 watcher 线程调用 accept()，
 *   flush() 也在 watcher 线程上同步执行(包含 DB 写入)。
 *   buffer 使用 synchronized 保证线程安全。
 */
public class EventAggregator {

    private final ActivityBlockGenerator generator;
    private final List<FileEvent> buffer = new ArrayList<>();
    private final List<ActivityBlock> completedBlocks = new ArrayList<>();
    private final Consumer<List<ActivityBlock>> onFlush;

    private final int batchSize;

    /**
     * @param classifier 类别分类器
     * @param onFlush    聚合完成后的回调(通常用于持久化到 ActivityRepository)
     */
    public EventAggregator(CategoryClassifier classifier, Consumer<List<ActivityBlock>> onFlush) {
        this.generator = new ActivityBlockGenerator(classifier, MergeConfig.DEFAULT);
        this.batchSize = 100;
        this.onFlush   = onFlush;
    }

    /**
     * @param classifier      类别分类器
     * @param projectDetector 项目识别器
     * @param onFlush         聚合完成后的回调
     */
    public EventAggregator(CategoryClassifier classifier, ProjectDetector projectDetector,
                           Consumer<List<ActivityBlock>> onFlush) {
        this.generator = new ActivityBlockGenerator(classifier, MergeConfig.DEFAULT, projectDetector);
        this.batchSize = 100;
        this.onFlush   = onFlush;
    }

    /**
     * @param classifier 类别分类器
     * @param config     聚合配置
     * @param batchSize  批量阈值(缓冲区满时自动触发聚合)
     * @param onFlush    聚合完成后的回调
     */
    public EventAggregator(CategoryClassifier classifier, MergeConfig config,
                           int batchSize, Consumer<List<ActivityBlock>> onFlush) {
        this.generator = new ActivityBlockGenerator(classifier, config);
        this.batchSize = batchSize;
        this.onFlush   = onFlush;
    }

    /**
     * 接收一条文件事件。缓冲区满时自动触发聚合 + 回调。
     *
     * @param event 新的文件事件
     */
    public void accept(FileEvent event) {
        synchronized (buffer) {
            buffer.add(event);
            if (buffer.size() >= batchSize) {
                flush();
            }
        }
    }

    /**
     * 批量接收文件事件。
     *
     * @param events 文件事件列表
     */
    public void acceptAll(List<FileEvent> events) {
        synchronized (buffer) {
            buffer.addAll(events);
            if (buffer.size() >= batchSize) {
                flush();
            }
        }
    }

    /**
     * 刷新缓冲区：聚合 → 回调 → 清空已完成块。
     * 应用退出时必须调用此方法，否则缓冲区中的事件会丢失。
     */
    public void flush() {
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            List<FileEvent> toProcess = new ArrayList<>(buffer);
            buffer.clear();

            // 聚合
            List<ActivityBlock> newBlocks = generator.generate(toProcess);
            completedBlocks.addAll(newBlocks);

            // 回调(持久化到 ActivityRepository)
            if (onFlush != null && !newBlocks.isEmpty()) {
                try {
                    onFlush.accept(newBlocks);
                } catch (Exception e) {
                    LogUtil.error("聚合回调执行失败: " + e.getMessage());
                }
            }

            LogUtil.info("聚合器产出 " + newBlocks.size() + " 个活动块");

            // 清空已完成块(已通过回调持久化，无需继续占用内存)
            completedBlocks.clear();
        }
    }

    /**
     * 获取当前缓冲区大小。
     */
    public int getBufferSize() {
        synchronized (buffer) {
            return buffer.size();
        }
    }

    /**
     * 获取累计产出的活动块数量(仅统计未 flush 的)。
     */
    public int getCompletedCount() {
        synchronized (buffer) {
            return completedBlocks.size();
        }
    }
}
