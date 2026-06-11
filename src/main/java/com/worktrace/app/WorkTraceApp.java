package com.worktrace.app;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.collector.EventAggregator;
import com.worktrace.collector.FileWatcherService;
import com.worktrace.collector.FileWatcherServiceImpl;
import com.worktrace.database.DatabaseManager;
import com.worktrace.database.dao.FileEventDao;
import com.worktrace.database.migration.DatabaseMigration;
import com.worktrace.model.FileEvent;
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
 * 启动流程：
 *   1. 初始化配置(Config)
 *   2. 初始化数据库(DatabaseManager + Migration)
 *   3. 初始化采集层(FileWatcherService + EventAggregator)
 *   4. 加载 FXML 主界面
 *   5. 注入依赖到 Controller
 *   6. 显示窗口
 */
public class WorkTraceApp extends Application {

    private FileWatcherService watcherService;

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

        // 3. 采集层
        CategoryClassifier classifier = new CategoryClassifier();
        EventAggregator aggregator     = new EventAggregator(classifier);
        FileEventDao fileEventDao      = new FileEventDao();

        watcherService = new FileWatcherServiceImpl();

        // 注册事件回调：FileEvent → 保存到 SQLite
        watcherService.addEventListener((eventType, filePath, size) -> {
            FileEvent event = new FileEvent();
            event.setEventType(eventType);
            event.setPath(filePath.toAbsolutePath().toString());
            event.setFileName(filePath.getFileName().toString());
            event.setExtension(extractExtension(filePath));
            event.setSize(size);
            event.setEventTime(LocalDateTime.now());
            try {
                fileEventDao.insert(event);
            } catch (Exception e) {
                LogUtil.error("保存文件事件失败: " + e.getMessage());
            }
        });

        // 从配置读取监听目录并启动
        List<Path> watchDirs = parseWatchDirs(config.getString("watch.dirs"));
        watcherService.watchDirectories(watchDirs);
        watcherService.start();
        LogUtil.info("已注册监听目录: " + watchDirs);

        // 4. 加载 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // 5. 注入依赖
        MainController controller = loader.getController();
        controller.setWatcherService(watcherService);
        // controller.setTimelineService(...);    // TODO: 后续注入
        // controller.setProjectService(...);
        // controller.setStatisticsService(...);

        // 6. 显示窗口
        Scene scene = new Scene(root, 960, 640);
        primaryStage.setTitle("WorkTrace - 个人工作轨迹");
        primaryStage.setScene(scene);
        primaryStage.show();

        LogUtil.info("WorkTrace 启动完成");
    }

    @Override
    public void stop() throws Exception {
        if (watcherService != null) {
            watcherService.stop();
        }
        DatabaseManager.getInstance().close();
        LogUtil.info("WorkTrace 已退出");
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
