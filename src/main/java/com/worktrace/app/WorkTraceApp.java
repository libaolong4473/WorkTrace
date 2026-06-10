package com.worktrace.app;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.collector.EventAggregator;
import com.worktrace.collector.FileWatcherService;
import com.worktrace.database.DatabaseManager;
import com.worktrace.database.migration.DatabaseMigration;
import com.worktrace.ui.controller.MainController;
import com.worktrace.util.Config;
import com.worktrace.util.LogUtil;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
        // watcherService = new FileWatcherServiceImpl();  // TODO: 第二阶段实现

        // 4. 加载 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // 5. 注入依赖
        MainController controller = loader.getController();
        // controller.setWatcherService(watcherService);   // TODO: 第二阶段注入
        // controller.setTimelineService(...);
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

    public static void main(String[] args) {
        launch(args);
    }
}
