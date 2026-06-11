package com.worktrace.ui.controller;

import com.worktrace.collector.FileWatcherService;
import com.worktrace.database.FileEventRepository;
import com.worktrace.database.ProjectRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.service.TimelineService;
import com.worktrace.ui.view.ActivityBlockCell;
import com.worktrace.ui.view.FileDetailCell;
import com.worktrace.ui.view.ProjectStatsCell;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 主界面控制器 —— 真实数据驱动。
 *
 * 数据流：
 *   SQLite → TimelineService / FileEventRepository → MainController → UI
 *
 * 刷新机制：
 *   - 启动时立即加载一次
 *   - WatchService 运行时，每 30 秒自动刷新
 *   - 使用 JavaFX Timeline 在 UI 线程上调度
 *
 * 线程模型：
 *   ┌────────────────────┐     ┌────────────────────┐
 *   │  JavaFX App Thread │     │  WatchService Thread│
 *   │  (UI 更新)         │     │  (事件采集)         │
 *   └────────┬───────────┘     └────────┬───────────┘
 *            │                          │
 *            │  Platform.runLater()     │  FileEventRepo.insert()
 *            │  Timeline (30s tick)     │  EventAggregator.accept()
 *            │                          │
 *            ▼                          ▼
 *   ┌────────────────────┐     ┌────────────────────┐
 *   │  loadAllData()     │     │  flush → batchInsert│
 *   │  → SQLite 读取     │     │  → SQLite 写入      │
 *   └────────────────────┘     └────────────────────┘
 */
public class MainController implements Initializable {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE");

    // ---------- 顶部状态栏 ----------
    @FXML private Label lblWatchStatus;
    @FXML private Label lblWatchedDirs;
    @FXML private Label lblEventCount;

    // ---------- 左侧边栏 ----------
    @FXML private Label lblBlockCount;
    @FXML private Label lblActiveMinutes;
    @FXML private Label lblCatCode;
    @FXML private Label lblCatDocument;
    @FXML private Label lblCatImage;
    @FXML private Label lblCatVideo;
    @FXML private Label lblCatConfig;
    @FXML private Button btnOverview;
    @FXML private Button btnTimeline;
    @FXML private Button btnProjects;
    @FXML private Button btnToggleWatch;

    // ---------- 中间时间线 ----------
    @FXML private Label lblTimelineTitle;
    @FXML private Label lblTimelineDate;
    @FXML private ListView<ActivityBlock> timelineList;

    // ---------- 右侧详情 ----------
    @FXML private Label lblDetailTitle;
    @FXML private Label lblDetailTime;
    @FXML private Label lblDetailDuration;
    @FXML private Label lblDetailFiles;
    @FXML private ListView<String> detailFileList;

    // ---------- 底部状态栏 ----------
    @FXML private Label lblStatusText;

    // ---------- 服务依赖 ----------
    private FileWatcherService watcherService;
    private TimelineService timelineService;
    private FileEventRepository fileEventRepository;
    private ProjectRepository projectRepository;
    private Runnable onStopCallback;

    // ---------- 状态 ----------
    private final ObservableList<ActivityBlock> blockData = FXCollections.observableArrayList();
    private final ObservableList<String> fileDetailData = FXCollections.observableArrayList();
    private final ObservableList<ProjectRepository.ProjectStats> projectStatsData = FXCollections.observableArrayList();
    private Button activeNavButton;
    private Timeline autoRefreshTimeline;

    // ---------- 根面板 + 视图切换 ----------
    @FXML private javafx.scene.layout.BorderPane rootPane;
    private String currentView = "overview";
    private javafx.scene.Node timelineCenterNode;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTimelineList();
        initDetailFileList();
        initNavigation();
        initWatchToggle();

        lblTimelineDate.setText(LocalDate.now().format(DATE_FMT));

        // 保存时间线中心节点(FXML 加载完成后赋值)
        javafx.application.Platform.runLater(() -> {
            timelineCenterNode = rootPane.getCenter();
        });
    }

    // ==================== 初始化 ====================

    private void initTimelineList() {
        timelineList.setItems(blockData);
        timelineList.setCellFactory(list -> new ActivityBlockCell());
        timelineList.setFixedCellSize(76);
        timelineList.setPlaceholder(new Label("暂无活动记录"));

        timelineList.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showBlockDetail(newVal);
                }
            }
        );
    }

    private void initDetailFileList() {
        detailFileList.setItems(fileDetailData);
        detailFileList.setCellFactory(list -> new FileDetailCell());
        detailFileList.setFixedCellSize(48);
        detailFileList.setPlaceholder(new Label("选择一个活动块"));
    }

    private void initNavigation() {
        activeNavButton = btnOverview;
        btnOverview.setOnAction(e -> switchView("overview", btnOverview));
        btnTimeline.setOnAction(e -> switchView("timeline", btnTimeline));
        btnProjects.setOnAction(e -> switchView("projects", btnProjects));
    }

    private void initWatchToggle() {
        btnToggleWatch.setOnAction(e -> {
            if (watcherService == null) return;
            if (watcherService.isRunning()) {
                // 停止监听：先停 WatchService，再 flush 聚合器
                if (onStopCallback != null) {
                    onStopCallback.run();
                } else {
                    watcherService.stop();
                }
                updateWatchStatus(false);
                btnToggleWatch.setText("▶  开始监听");
                // 停止后立即刷新 UI，显示缓冲区中聚合出的活动块
                loadAllData();
            } else {
                watcherService.start();
                updateWatchStatus(true);
                btnToggleWatch.setText("■  停止监听");
            }
        });
    }

    // ==================== 自动刷新 ====================

    /**
     * 启动 30 秒自动刷新。仅在 WatchService 运行时有意义。
     * 使用 JavaFX Timeline 保证 tick 在 UI 线程执行。
     */
    private void startAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(30), e -> loadAllData())
        );
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
    }

    // ==================== 数据加载 ====================

    /**
     * 加载全部数据：时间线 + 类别统计 + 概览数字。
     * 在 JavaFX App Thread 上执行，可安全更新 UI。
     */
    private void loadAllData() {
        if (timelineService == null) return;

        LocalDate today = LocalDate.now();

        // 查询
        List<ActivityBlock> blocks    = timelineService.getDailyTimeline(today);
        long totalMinutes             = timelineService.getTotalActiveMinutes(today);
        Map<String, Long> catDuration = timelineService.getCategoryDuration(today);

        // 更新时间线
        blockData.setAll(blocks);
        if (!blocks.isEmpty()) {
            timelineList.getSelectionModel().selectFirst();
        }

        // 更新左侧概览
        lblBlockCount.setText(String.valueOf(blocks.size()));
        lblActiveMinutes.setText(String.valueOf(totalMinutes));

        // 更新类别统计
        lblCatCode.setText(catDuration.getOrDefault("CODE", 0L) + " 分钟");
        lblCatDocument.setText(catDuration.getOrDefault("DOCUMENT", 0L) + " 分钟");
        lblCatImage.setText(catDuration.getOrDefault("IMAGE", 0L) + " 分钟");
        lblCatVideo.setText(catDuration.getOrDefault("VIDEO", 0L) + " 分钟");
        lblCatConfig.setText(catDuration.getOrDefault("CONFIG", 0L) + " 分钟");

        // 更新顶部统计
        lblEventCount.setText("今日: " + blocks.size() + " 块 / " + totalMinutes + " 分钟");

        lblStatusText.setText("已加载 " + blocks.size() + " 个活动块 · " + today.format(DATE_FMT));
    }

    // ==================== 右侧详情 ====================

    /**
     * 显示选中的活动块详情。
     * 从 file_event 表查询该时间段内涉及的真实文件列表。
     */
    private void showBlockDetail(ActivityBlock block) {
        lblDetailTitle.setText(block.getSummary());
        lblDetailTime.setText(
            block.getStartTime().format(TIME_FMT) + " - " +
            block.getEndTime().format(TIME_FMT)
        );

        long minutes = java.time.Duration.between(block.getStartTime(), block.getEndTime()).toMinutes();
        lblDetailDuration.setText(String.valueOf(Math.max(minutes, 1)));

        // 更新类别样式
        lblDetailTitle.getStyleClass().removeIf(s -> s.startsWith("card-category-"));
        lblDetailTitle.getStyleClass().add("card-category-" + block.getCategory().toLowerCase());

        // 从 file_event 表查询该时间段的真实文件列表
        fileDetailData.clear();
        if (fileEventRepository != null) {
            try {
                List<FileEvent> files = fileEventRepository.findByTimeRange(
                    block.getStartTime(), block.getEndTime().plusSeconds(1)
                );
                // 按路径去重
                java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                for (FileEvent f : files) {
                    seen.add(f.getPath());
                }
                fileDetailData.addAll(seen);
                lblDetailFiles.setText(String.valueOf(seen.size()));
            } catch (Exception e) {
                fileDetailData.add("查询失败: " + e.getMessage());
                lblDetailFiles.setText("-");
            }
        } else {
            fileDetailData.add("(持久化层未初始化)");
            lblDetailFiles.setText("-");
        }

        lblStatusText.setText("查看: " + block.getSummary());
    }

    // ==================== 导航切换 ====================

    private void switchView(String view, Button source) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
        }
        source.getStyleClass().add("nav-button-active");
        activeNavButton = source;
        currentView = view;

        if ("projects".equals(view)) {
            // 切换到项目统计视图
            rootPane.setCenter(createProjectStatsPanel());
            loadProjectStats();
            lblStatusText.setText("项目统计");
        } else {
            // 切换回时间线视图
            if (timelineCenterNode != null) {
                rootPane.setCenter(timelineCenterNode);
            }
            lblTimelineTitle.setText(switch (view) {
                case "overview" -> "今日时间线";
                case "timeline" -> "全部时间线";
                default -> "时间线";
            });
            lblStatusText.setText("切换到: " + source.getText().trim());
        }
    }

    /**
     * 创建项目统计面板。
     */
    private VBox createProjectStatsPanel() {
        Label title = new Label("项目统计");
        title.getStyleClass().add("timeline-header");

        Label date = new Label(LocalDate.now().format(DATE_FMT));
        date.getStyleClass().add("timeline-date");

        HBox header = new HBox(12, title, new Region(), date);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ListView<ProjectRepository.ProjectStats> statsList = new ListView<>(projectStatsData);
        statsList.setCellFactory(list -> new ProjectStatsCell());
        statsList.setFixedCellSize(90);
        statsList.setPlaceholder(new Label("暂无项目数据"));
        statsList.getStyleClass().add("timeline-list");

        VBox panel = new VBox(8, header, statsList);
        VBox.setVgrow(statsList, Priority.ALWAYS);
        panel.setPadding(new javafx.geometry.Insets(12));
        panel.getStyleClass().add("timeline-panel");
        return panel;
    }

    /**
     * 加载项目统计数据。
     */
    private void loadProjectStats() {
        if (projectRepository == null) return;
        try {
            var stats = projectRepository.getProjectStats(LocalDate.now());
            projectStatsData.setAll(stats);
            lblStatusText.setText("已加载 " + stats.size() + " 个项目");
        } catch (Exception e) {
            lblStatusText.setText("加载项目统计失败: " + e.getMessage());
        }
    }

    // ==================== 状态更新 ====================

    public void updateWatchStatus(boolean running) {
        Platform.runLater(() -> {
            if (running) {
                lblWatchStatus.setText("● 监听中");
                lblWatchStatus.getStyleClass().removeAll("status-stopped");
                lblWatchStatus.getStyleClass().add("status-running");
                startAutoRefresh();
            } else {
                lblWatchStatus.setText("○ 已停止");
                lblWatchStatus.getStyleClass().removeAll("status-running");
                lblWatchStatus.getStyleClass().add("status-stopped");
                stopAutoRefresh();
            }
        });
    }

    // ==================== 依赖注入 ====================

    public void setWatcherService(FileWatcherService service) {
        this.watcherService = service;
        if (service != null) {
            Platform.runLater(() -> {
                updateWatchStatus(service.isRunning());
                if (service.isRunning()) {
                    btnToggleWatch.setText("■  停止监听");
                }
                if (lblWatchedDirs != null) {
                    String dirs = service.getWatchedDirectories().stream()
                        .map(p -> p.getFileName().toString())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("未配置");
                    lblWatchedDirs.setText("监听目录: " + dirs);
                }
            });
        }
    }

    public void setTimelineService(TimelineService service) {
        this.timelineService = service;
        // 注入后立即加载今日数据
        Platform.runLater(this::loadAllData);
    }

    public void setFileEventRepository(FileEventRepository repo) {
        this.fileEventRepository = repo;
    }

    public void setProjectRepository(ProjectRepository repo) {
        this.projectRepository = repo;
    }

    public void setOnStopCallback(Runnable callback) {
        this.onStopCallback = callback;
    }
}
