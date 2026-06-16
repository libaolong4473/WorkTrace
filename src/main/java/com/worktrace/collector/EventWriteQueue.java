package com.worktrace.collector;

import com.worktrace.database.FileEventRepository;
import com.worktrace.model.FileEvent;
import com.worktrace.util.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 高性能异步写入队列。
 *
 * 架构：
 *   WatchService Thread → queue.offer(event) → 零阻塞
 *                              ↓
 *   Writer Thread → queue.take() → 批量写入 SQLite
 *
 * 特性：
 *   - 容量 5000，满时丢弃最旧事件（不阻塞采集线程）
 *   - 独立守护线程，每 100 条或每 1 秒刷一次盘
 *   - 应用退出时 drain 剩余事件，不丢数据
 */
public class EventWriteQueue {

    private static final int QUEUE_CAPACITY = 5000;
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 1000;

    private final ArrayBlockingQueue<FileEvent> queue;
    private final FileEventRepository repository;
    private final EventAggregator aggregator;
    private Thread writerThread;
    private volatile boolean running = false;

    public EventWriteQueue(FileEventRepository repository, EventAggregator aggregator) {
        this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.repository = repository;
        this.aggregator = aggregator;
    }

    /**
     * 启动写入线程。
     */
    public void start() {
        if (running) return;
        running = true;
        writerThread = new Thread(this::drainLoop, "worktrace-writer");
        writerThread.setDaemon(true);
        writerThread.start();
        LogUtil.info("EventWriteQueue 已启动 (容量=" + QUEUE_CAPACITY + ")");
    }

    /**
     * 停止写入线程并 drain 剩余事件。
     */
    public void stop() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
        drainRemaining();
        LogUtil.info("EventWriteQueue 已停止");
    }

    /**
     * 提交事件到队列（非阻塞）。
     * 队列满时丢弃最旧事件，绝不阻塞 WatchService 线程。
     */
    public void submit(FileEvent event) {
        if (!queue.offer(event)) {
            // 队列满，丢弃最旧的
            queue.poll();
            queue.offer(event);
        }
    }

    /**
     * 当前队列积压量。
     */
    public int size() {
        return queue.size();
    }

    // ==================== 内部实现 ====================

    /**
     * 消费循环：每 100 条或每 1 秒刷一次盘。
     */
    private void drainLoop() {
        List<FileEvent> batch = new ArrayList<>(BATCH_SIZE);
        while (running) {
            try {
                // 阻塞等待第一条
                FileEvent first = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    // 非阻塞收集剩余
                    queue.drainTo(batch, BATCH_SIZE - 1);
                }

                if (!batch.isEmpty()) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 批量写入 SQLite + 喂入聚合器。
     */
    private void flushBatch(List<FileEvent> batch) {
        try {
            repository.batchInsert(batch);
        } catch (Exception e) {
            LogUtil.error("批量写入 file_event 失败: " + e.getMessage());
        }

        // 喂入聚合器
        if (aggregator != null) {
            aggregator.acceptAll(batch);
        }
    }

    /**
     * 应用退出时 drain 剩余事件。
     */
    private void drainRemaining() {
        List<FileEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            LogUtil.info("drain 剩余 " + remaining.size() + " 条事件");
            flushBatch(remaining);
        }
        // 最后一次 flush 聚合器
        if (aggregator != null) {
            aggregator.flush();
        }
    }
}
