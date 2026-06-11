package com.worktrace.collector;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件监视服务接口。
 * 基于 NIO WatchService 封装，监听指定目录的文件变化事件。
 *
 * 职责：
 *   - 启动 / 停止监听
 *   - 注册 / 注销监听目录
 *   - 将原始 WatchEvent 转换为 FileEvent 并通知观察者
 *
 * 设计说明：
 *   使用观察者模式，内部线程持续轮询 WatchService，
 *   检测到变化后通过回调通知 EventAggregator。
 */
public interface FileWatcherService {

    /** 启动监听线程。 */
    void start();

    /** 停止监听线程。 */
    void stop();

    /** 添加一个监听目录(自动递归注册子目录)。 */
    void watchDirectory(Path dir);

    /** 批量添加监听目录。 */
    void watchDirectories(List<Path> dirs);

    /** 移除一个监听目录。 */
    void unwatchDirectory(Path dir);

    /** 获取当前监听的目录列表。 */
    List<Path> getWatchedDirectories();

    /** 注册事件回调。 */
    void addEventListener(FileEventListener listener);

    /** 当前是否正在监听。 */
    boolean isRunning();

    /**
     * 文件事件回调接口。
     */
    interface FileEventListener {
        void onFileEvent(String eventType, Path filePath, long size);
    }
}
