package com.worktrace.ui.controller;

import com.worktrace.collector.FileWatcherService;
import com.worktrace.service.StatisticsService;
import com.worktrace.service.TimelineService;
import com.worktrace.service.ProjectService;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

/**
 * 主界面控制器。
 * 绑定 BorderPane 各区域的子视图，协调各 Service 的数据输出。
 *
 * 职责：
 *   - 初始化左侧导航(今日概览、时间线、项目统计)
 *   - 初始化右侧活动列表
 *   - 更新顶部监听状态指示
 *   - 响应用户导航切换
 */
public class MainController implements Initializable {

    // ---------- 顶部状态栏 ----------
    @FXML private Label lblWatchStatus;
    @FXML private Label lblWatchedDirs;

    // ---------- 左侧面板 ----------
    @FXML private VBox sidebarBox;
    @FXML private Label lblTodaySummary;
    @FXML private TreeView<String> navTree;

    // ---------- 右侧主区域 ----------
    @FXML private ListView<String> activityList;

    // ---------- 底部状态栏 ----------
    @FXML private Label lblEventCount;

    // ---------- 服务依赖(由 App 注入) ----------
    private FileWatcherService watcherService;
    private TimelineService timelineService;
    private ProjectService projectService;
    private StatisticsService statisticsService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initNavigationTree();
        updateWatchStatus(false);
    }

    private void initNavigationTree() {
        // TODO: 构建左侧导航树(今日概览 / 时间线 / 项目统计)
    }

    /**
     * 更新顶部监听状态文字。
     */
    public void updateWatchStatus(boolean running) {
        if (running) {
            lblWatchStatus.setText("● 监听中");
            lblWatchStatus.setStyle("-fx-text-fill: #4CAF50;");
        } else {
            lblWatchStatus.setText("○ 已停止");
            lblWatchStatus.setStyle("-fx-text-fill: #F44336;");
        }
    }

    /**
     * 刷新今日概览数据。
     */
    public void refreshTodayOverview() {
        // TODO: 调用 StatisticsService 填充今日概览
    }

    /**
     * 刷新活动列表。
     */
    public void refreshActivityList() {
        // TODO: 调用 TimelineService 填充活动列表
    }

    // ---------- 依赖注入 ----------

    public void setWatcherService(FileWatcherService service) {
        this.watcherService = service;
        if (service != null) {
            updateWatchStatus(service.isRunning());
            if (lblWatchedDirs != null) {
                String dirs = service.getWatchedDirectories().stream()
                    .map(p -> p.getFileName().toString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("未配置");
                lblWatchedDirs.setText("监听目录: " + dirs);
            }
        }
    }
    public void setTimelineService(TimelineService service)      { this.timelineService = service; }
    public void setProjectService(ProjectService service)        { this.projectService = service; }
    public void setStatisticsService(StatisticsService service)  { this.statisticsService = service; }
}
