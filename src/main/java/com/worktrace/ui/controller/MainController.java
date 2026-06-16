package com.worktrace.ui.controller;

import com.worktrace.collector.FileWatcherService;
import com.worktrace.database.ActivityRepository;
import com.worktrace.database.FileEventRepository;
import com.worktrace.database.ProjectRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.model.WorkSession;
import com.worktrace.service.TimelineService;
import com.worktrace.service.WorkSessionService;
import com.worktrace.ui.view.ActivityBlockCell;
import com.worktrace.ui.view.FileDetailCell;
import com.worktrace.ui.view.ProjectStatsCell;
import com.worktrace.ui.view.WorkSessionCell;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.Scene;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 主界面控制器 —— 以 WorkSession 为核心展示。
 *
 * 展示层级：
 *   WorkSession（一级）→ 时间线主列表
 *     └── ActivityBlock（二级）→ 点击 WorkSession 后右侧展示
 *           └── FileEvent（三级）→ 点击 ActivityBlock 后文件列表展示
 *
 * 数据流：
 *   SQLite → WorkSessionService → MainController → UI
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
    @FXML private DatePicker datePicker;
    @FXML private Button btnToday;
    @FXML private Button btnYesterday;
    @FXML private Button btnLast7Days;
    @FXML private Button btnLast30Days;
    @FXML private ListView<WorkSession> timelineList;

    // ---------- 右侧详情 ----------
    @FXML private Label lblDetailTitle;
    @FXML private Label lblDetailTime;
    @FXML private Label lblDetailDuration;
    @FXML private Label lblDetailFiles;
    @FXML private ListView<ActivityBlock> detailBlockList;

    // ---------- 底部状态栏 ----------
    @FXML private Label lblStatusText;

    // ---------- 服务依赖 ----------
    private FileWatcherService watcherService;
    private WorkSessionService workSessionService;
    private TimelineService timelineService;
    private FileEventRepository fileEventRepository;
    private ActivityRepository activityRepository;
    private ProjectRepository projectRepository;
    private Runnable onStopCallback;

    // ---------- 状态 ----------
    private LocalDate selectedDate = LocalDate.now();

    // ---------- 状态 ----------
    private final ObservableList<WorkSession> sessionData = FXCollections.observableArrayList();
    private final ObservableList<ActivityBlock> blockDetailData = FXCollections.observableArrayList();
    private final ObservableList<ProjectRepository.ProjectStats> projectStatsData = FXCollections.observableArrayList();
    private Button activeNavButton;
    private Timeline autoRefreshTimeline;

    // ---------- 根面板 + 视图切换 ----------
    @FXML private BorderPane rootPane;
    private String currentView = "overview";
    private javafx.scene.Node timelineCenterNode;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTimelineList();
        initDetailBlockList();
        initNavigation();
        initWatchToggle();
        initDatePicker();

        Platform.runLater(() -> {
            timelineCenterNode = rootPane.getCenter();
        });
    }

    // ==================== 初始化 ====================

    private void initTimelineList() {
        timelineList.setItems(sessionData);
        timelineList.setCellFactory(list -> new WorkSessionCell());
        timelineList.setFixedCellSize(90);
        timelineList.setPlaceholder(new Label("暂无工作记录"));

        // 点击 WorkSession → 右侧展示其 ActivityBlock 列表
        timelineList.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showSessionDetail(newVal);
                }
            }
        );
    }

    private void initDetailBlockList() {
        detailBlockList.setItems(blockDetailData);
        detailBlockList.setCellFactory(list -> new ActivityBlockCell());
        detailBlockList.setFixedCellSize(76);
        detailBlockList.setPlaceholder(new Label("选择一个工作会话"));

        // 点击 ActivityBlock → 展示文件列表
        detailBlockList.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showBlockFiles(newVal);
                }
            }
        );
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
                if (onStopCallback != null) {
                    onStopCallback.run();
                } else {
                    watcherService.stop();
                }
                updateWatchStatus(false);
                btnToggleWatch.setText("▶  开始监听");
                loadAllData();
            } else {
                watcherService.start();
                updateWatchStatus(true);
                btnToggleWatch.setText("■  停止监听");
            }
        });
    }

    // ==================== 日期选择 ====================

    private void initDatePicker() {
        // DatePicker 默认今天
        datePicker.setValue(LocalDate.now());
        datePicker.getStyleClass().add("date-picker");

        // DatePicker 切换日期
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(selectedDate)) {
                changeDate(newVal);
            }
        });

        // 快捷按钮
        btnToday.setOnAction(e -> changeDate(LocalDate.now()));
        btnYesterday.setOnAction(e -> changeDate(LocalDate.now().minusDays(1)));
        btnLast7Days.setOnAction(e -> {
            selectedDate = LocalDate.now().minusDays(6);
            datePicker.setValue(null); // 范围模式，清空 DatePicker
            loadDateRange(LocalDate.now().minusDays(6), LocalDate.now());
            highlightQuickButton(btnLast7Days);
        });
        btnLast30Days.setOnAction(e -> {
            selectedDate = LocalDate.now().minusDays(29);
            datePicker.setValue(null);
            loadDateRange(LocalDate.now().minusDays(29), LocalDate.now());
            highlightQuickButton(btnLast30Days);
        });

        // 默认高亮"今天"
        highlightQuickButton(btnToday);
    }

    private void changeDate(LocalDate date) {
        selectedDate = date;
        datePicker.setValue(date);
        loadAllData();
        highlightQuickButton(null); // 清空快捷按钮高亮
    }

    private void highlightQuickButton(Button active) {
        for (Button btn : new Button[]{btnToday, btnYesterday, btnLast7Days, btnLast30Days}) {
            btn.getStyleClass().remove("date-quick-btn-active");
        }
        if (active != null) {
            active.getStyleClass().add("date-quick-btn-active");
        }
    }

    /**
     * 加载日期范围数据（最近7天/最近30天）。
     */
    private void loadDateRange(LocalDate from, LocalDate to) {
        if (workSessionService == null) return;

        // 查询范围内的 WorkSession
        List<WorkSession> sessions = workSessionService.getSessionsByDateRange(from, to);
        sessionData.setAll(sessions);

        // 更新概览
        long totalMinutes = sessions.stream().mapToLong(WorkSession::getDurationMinutes).sum();
        lblBlockCount.setText(String.valueOf(sessions.size()));
        lblActiveMinutes.setText(String.valueOf(totalMinutes));

        // 更新标题
        long days = java.time.Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays();
        lblTimelineTitle.setText("最近 " + days + " 天");
        lblEventCount.setText(sessions.size() + " 个工作会话 / " + totalMinutes + " 分钟");
        lblStatusText.setText("已加载 " + from + " 至 " + to + " 的数据");

        // 类别统计（使用 TimelineService 的范围查询）
        if (timelineService != null) {
            Map<String, Long> catDuration = timelineService.getCategoryDurationByRange(from, to);
            lblCatCode.setText(catDuration.getOrDefault("CODE", 0L) + " 分钟");
            lblCatDocument.setText(catDuration.getOrDefault("DOCUMENT", 0L) + " 分钟");
            lblCatImage.setText(catDuration.getOrDefault("IMAGE", 0L) + " 分钟");
            lblCatVideo.setText(catDuration.getOrDefault("VIDEO", 0L) + " 分钟");
            lblCatConfig.setText(catDuration.getOrDefault("CONFIG", 0L) + " 分钟");
        }
    }

    // ==================== 自动刷新 ====================

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

    // ==================== 数据加载（异步） ====================

    /**
     * 异步加载全部数据：WorkSession + 类别统计。
     * 数据库查询在后台线程执行，UI 更新通过 Platform.runLater 回调。
     */
    private void loadAllData() {
        LocalDate date = selectedDate;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                // 后台线程：查询数据
                List<WorkSession> sessions = List.of();
                Map<String, Long> catDuration = Map.of();

                if (workSessionService != null) {
                    sessions = workSessionService.getDailySessions(date);
                }
                if (timelineService != null) {
                    catDuration = timelineService.getCategoryDuration(date);
                }

                // 回调 UI 线程
                final List<WorkSession> finalSessions = sessions;
                final Map<String, Long> finalCatDuration = catDuration;
                Platform.runLater(() -> updateUI(finalSessions, finalCatDuration, date));
                return null;
            }
        };
        new Thread(task).start();
    }

    /**
     * 在 JavaFX 线程上更新 UI 控件。
     */
    private void updateUI(List<WorkSession> sessions, Map<String, Long> catDuration, LocalDate date) {
        // 时间线
        sessionData.setAll(sessions);
        if (!sessions.isEmpty()) {
            timelineList.getSelectionModel().selectFirst();
        }

        // 概览数字
        long totalMinutes = sessions.stream().mapToLong(WorkSession::getDurationMinutes).sum();
        lblBlockCount.setText(String.valueOf(sessions.size()));
        lblActiveMinutes.setText(String.valueOf(totalMinutes));

        // 标题
        String dateLabel = date.equals(LocalDate.now()) ? "今日" : date.toString();
        lblTimelineTitle.setText(dateLabel + "工作会话");
        lblEventCount.setText(dateLabel + ": " + sessions.size() + " 个会话 / " + totalMinutes + " 分钟");

        // 类别统计
        lblCatCode.setText(catDuration.getOrDefault("CODE", 0L) + " 分钟");
        lblCatDocument.setText(catDuration.getOrDefault("DOCUMENT", 0L) + " 分钟");
        lblCatImage.setText(catDuration.getOrDefault("IMAGE", 0L) + " 分钟");
        lblCatVideo.setText(catDuration.getOrDefault("VIDEO", 0L) + " 分钟");
        lblCatConfig.setText(catDuration.getOrDefault("CONFIG", 0L) + " 分钟");

        lblStatusText.setText("已加载 " + sessions.size() + " 个会话 · " + date.format(DATE_FMT));
    }

    // ==================== 右侧详情：WorkSession → ActivityBlock 列表 ====================

    /**
     * 显示选中的 WorkSession 详情。
     * 查询该时间段内的 ActivityBlock 列表，展示在右侧。
     */
    private void showSessionDetail(WorkSession session) {
        // 更新详情头部
        lblDetailTitle.setText(session.getTitle());
        lblDetailTime.setText(
            session.getStartTime().format(TIME_FMT) + " - " +
            session.getEndTime().format(TIME_FMT)
        );
        lblDetailDuration.setText(session.getDurationMinutes() + " 分钟");
        lblDetailFiles.setText(session.getBlockCount() + " 个活动块");

        // 更新类别样式
        lblDetailTitle.getStyleClass().removeIf(s -> s.startsWith("card-category-"));
        lblDetailTitle.getStyleClass().add("card-category-" + session.getCategory().toLowerCase());

        // 查询该时间段内的 ActivityBlock
        blockDetailData.clear();
        if (activityRepository != null) {
            try {
                List<ActivityBlock> blocks = activityRepository.findByTimeRange(
                    session.getStartTime(), session.getEndTime().plusSeconds(1)
                );
                blockDetailData.setAll(blocks);
            } catch (Exception e) {
                lblStatusText.setText("查询活动块失败: " + e.getMessage());
            }
        }

        // 清空文件列表（等用户点击 ActivityBlock 时再加载）
        lblStatusText.setText("查看: " + session.getTitle());
    }

    // ==================== 三级详情：ActivityBlock → 文件列表 ====================

    /**
     * 显示选中的 ActivityBlock 的文件列表。
     * 直接在右侧详情面板下方展示文件列表。
     */
    private void showBlockFiles(ActivityBlock block) {
        if (fileEventRepository == null) return;

        try {
            List<FileEvent> files = fileEventRepository.findByTimeRange(
                block.getStartTime(), block.getEndTime().plusSeconds(1)
            );

            // 按路径去重
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (FileEvent f : files) {
                seen.add(f.getPath());
            }

            // 更新文件数显示
            lblDetailFiles.setText(seen.size() + " 个文件");

            // 弹出文件列表窗口
            showFileListPopup(block.getSummary(), seen);
        } catch (Exception e) {
            lblStatusText.setText("查询文件失败: " + e.getMessage());
        }
    }

    /**
     * 弹出文件列表窗口，支持右键打开文件/打开所在目录。
     */
    private void showFileListPopup(String title, java.util.Set<String> filePaths) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.setTitle("文件列表 - " + title);

        VBox content = new VBox(8);
        content.setPadding(new javafx.geometry.Insets(12));

        Label header = new Label(title);
        header.getStyleClass().add("timeline-header");

        ListView<String> fileList = new ListView<>();
        fileList.getItems().addAll(filePaths);
        fileList.setCellFactory(list -> new FileDetailCell());
        fileList.setFixedCellSize(48);

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();

        MenuItem openFile = new MenuItem("打开文件");
        openFile.setOnAction(e -> {
            String selected = fileList.getSelectionModel().getSelectedItem();
            if (selected != null) openFile(selected);
        });

        MenuItem openDir = new MenuItem("打开所在目录");
        openDir.setOnAction(e -> {
            String selected = fileList.getSelectionModel().getSelectedItem();
            if (selected != null) openFileLocation(selected);
        });

        contextMenu.getItems().addAll(openFile, openDir);

        // 右键显示菜单
        fileList.setContextMenu(contextMenu);

        // 双击打开文件
        fileList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = fileList.getSelectionModel().getSelectedItem();
                if (selected != null) openFile(selected);
            }
        });

        content.getChildren().addAll(header, fileList);
        VBox.setVgrow(fileList, Priority.ALWAYS);

        Scene scene = new Scene(content, 500, 400);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        popup.setScene(scene);
        popup.show();
    }

    /**
     * 用系统默认程序打开文件。
     */
    private void openFile(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                lblStatusText.setText("文件不存在: " + filePath);
            }
        } catch (Exception e) {
            lblStatusText.setText("打开文件失败: " + e.getMessage());
        }
    }

    /**
     * 在资源管理器中打开文件所在目录并选中文件。
     */
    private void openFileLocation(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                // explorer /select,"path" 打开目录并选中文件
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", filePath});
            } else {
                // 文件不存在时打开其父目录
                java.io.File parent = file.getParentFile();
                if (parent != null && parent.exists()) {
                    java.awt.Desktop.getDesktop().open(parent);
                }
            }
        } catch (Exception e) {
            lblStatusText.setText("打开目录失败: " + e.getMessage());
        }
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
            rootPane.setCenter(createProjectStatsPanel());
            loadProjectStats();
            lblStatusText.setText("项目统计");
        } else {
            if (timelineCenterNode != null) {
                rootPane.setCenter(timelineCenterNode);
            }
            lblTimelineTitle.setText(switch (view) {
                case "overview" -> "今日工作会话";
                case "timeline" -> "全部工作会话";
                default -> "工作会话";
            });
            lblStatusText.setText("切换到: " + source.getText().trim());
        }
    }

    private VBox createProjectStatsPanel() {
        Label title = new Label("项目统计");
        title.getStyleClass().add("timeline-header");

        Label date = new Label(selectedDate.format(DATE_FMT));
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

    private void loadProjectStats() {
        if (projectRepository == null) return;
        try {
            var stats = projectRepository.getProjectStats(selectedDate);
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

    public void setWorkSessionService(WorkSessionService service) {
        this.workSessionService = service;
        Platform.runLater(this::loadAllData);
    }

    public void setTimelineService(TimelineService service) {
        this.timelineService = service;
    }

    public void setFileEventRepository(FileEventRepository repo) {
        this.fileEventRepository = repo;
    }

    public void setActivityRepository(ActivityRepository repo) {
        this.activityRepository = repo;
    }

    public void setProjectRepository(ProjectRepository repo) {
        this.projectRepository = repo;
    }

    public void setOnStopCallback(Runnable callback) {
        this.onStopCallback = callback;
    }
}
