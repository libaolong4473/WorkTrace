package com.worktrace.app;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.collector.EventAggregator;
import com.worktrace.collector.FileWatcherService;
import com.worktrace.collector.FileWatcherServiceImpl;
import com.worktrace.database.ActivityRepository;
import com.worktrace.database.DatabaseManager;
import com.worktrace.database.FileEventRepository;
import com.worktrace.database.migration.DatabaseMigration;
import com.worktrace.model.FileEvent;
import com.worktrace.service.TimelineService;
import com.worktrace.service.impl.TimelineServiceImpl;
import com.worktrace.ui.controller.MainController;
import com.worktrace.util.Config;
import com.worktrace.util.LogUtil;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WorkTrace 应用入口。
 *
 * 完整事件流：
 *   WatchService
 *       → listener
 *           → FileEventRepository.insert(event)      // 原始事件 → file_event 表
 *           → EventAggregator.accept(event)           // 喂入聚合缓冲区
 *               → 缓冲区满 → flush()
 *                   → ActivityBlockGenerator.generate()
 *                   → ActivityRepository.batchInsert() // 聚合结果 → activity_block 表
 *
 * 启动流程：
 *   1. 初始化配置(Config)
 *   2. 初始化数据库(DatabaseManager + Migration)
 *   3. 初始化持久化层(FileEventRepository + ActivityRepository)
 *   4. 初始化采集层(EventAggregator + FileWatcherService)
 *   5. 加载 FXML 主界面
 *   6. 注入依赖到 Controller
 *   7. 显示窗口
 */
public class WorkTraceApp extends Application {

    private FileWatcherService watcherService;
    private EventAggregator aggregator;

    @Override
    public void start(Stage primaryStage) throws Exception {
        LogUtil.info("WorkTrace 启动中...");

        // 1. 配置
        Config config = Config.getInstance();

        // 2. 数据库
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.initialize();
        new DatabaseMigration().migrate();
        LogUtil.info("数据库初始化完成");

        // 3. 持久化层
        FileEventRepository fileEventRepo     = new FileEventRepository();
        ActivityRepository activityRepo        = new ActivityRepository();

        // 4. 采集层 — 完整事件流接线
        CategoryClassifier classifier = new CategoryClassifier();

        // EventAggregator：聚合完成后自动写入 activity_block 表
        aggregator = new EventAggregator(
            classifier,
            blocks -> {
                try {
                    activityRepo.batchInsert(blocks);
                    LogUtil.info("已持久化 " + blocks.size() + " 个活动块到 activity_block");
                } catch (Exception e) {
                    LogUtil.error("活动块持久化失败: " + e.getMessage());
                }
            }
        );

        watcherService = new FileWatcherServiceImpl();

        // 监听回调：双写 — 原始事件 + 聚合器
        watcherService.addEventListener((eventType, filePath, size) -> {
            FileEvent event = new FileEvent();
            event.setEventType(eventType);
            event.setPath(filePath.toAbsolutePath().toString());
            event.setFileName(filePath.getFileName().toString());
            event.setExtension(extractExtension(filePath));
            event.setSize(size);
            event.setEventTime(LocalDateTime.now());

            // 1) 原始事件写入 file_event 表
            try {
                fileEventRepo.insert(event);
            } catch (Exception e) {
                LogUtil.error("保存文件事件失败: " + e.getMessage());
            }

            // 2) 喂入聚合器(缓冲区满时自动触发聚合 + 持久化)
            aggregator.accept(event);
        });

        // 从配置读取监听目录并启动
        List<Path> watchDirs = parseWatchDirs(config.getString("watch.dirs"));
        watcherService.watchDirectories(watchDirs);
        watcherService.start();
        LogUtil.info("已注册监听目录: " + watchDirs);

        // 5. 业务服务层
        TimelineService timelineService = new TimelineServiceImpl(activityRepo);

        // 6. 加载 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // 7. 注入依赖
        MainController controller = loader.getController();
        controller.setWatcherService(watcherService);
        controller.setTimelineService(timelineService);
        controller.setFileEventRepository(fileEventRepo);
        controller.setOnStopCallback(this::stopWatching);

        // 8. 显示窗口
        Scene scene = new Scene(root, 960, 640);
        primaryStage.setTitle("WorkTrace - 个人工作轨迹");
        primaryStage.setScene(scene);
        primaryStage.show();

        LogUtil.info("WorkTrace 启动完成");
    }

    @Override
    public void stop() throws Exception {
        stopWatching();
        DatabaseManager.getInstance().close();
        LogUtil.info("WorkTrace 已退出");
    }

    /**
     * 停止监听并刷新聚合器。
     * 由 MainController 的"停止监听"按钮调用。
     * 顺序：先停 WatchService → 再 flush 缓冲区 → 事件不丢失。
     */
    public void stopWatching() {
        if (watcherService != null) {
            watcherService.stop();
        }
        if (aggregator != null) {
            aggregator.flush();
            LogUtil.info("聚合器缓冲区已刷新");
        }
    }

    /**
     * 解析配置中的监听目录列表(分号分隔)。
     */
    private List<Path> parseWatchDirs(String dirs) {
        if (dirs == null || dirs.isBlank()) {
            return List.of(Path.of(System.getProperty("user.home"), "Desktop"));
        }
        return Arrays.stream(dirs.split(";"))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .map(Path::of)
                     .collect(Collectors.toList());
    }

    /**
     * 提取文件扩展名(不含点)，无扩展名返回空字符串。
     */
    private String extractExtension(Path filePath) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
