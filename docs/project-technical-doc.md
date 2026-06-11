# WorkTrace 项目技术文档 v0.1

**生成日期：** 2026-06-11
**分支：** dev (commit 4963f2b)

---

## 1. 项目目录树

```
WorkTrace/
├── .gitignore
├── pom.xml
├── README.md
├── docs/
│   ├── first-audit-report.md
│   └── project-technical-doc.md          ← 本文件
└── src/
    └── main/
        ├── java/
        │   ├── module-info.java
        │   └── com/
        │       └── worktrace/
        │           ├── app/
        │           │   └── WorkTraceApp.java
        │           ├── collector/
        │           │   ├── CategoryClassifier.java
        │           │   ├── EventAggregator.java
        │           │   ├── FileWatcherService.java
        │           │   └── FileWatcherServiceImpl.java
        │           ├── database/
        │           │   ├── ActivityRepository.java
        │           │   ├── DatabaseManager.java
        │           │   ├── FileEventRepository.java
        │           │   ├── PageResult.java
        │           │   ├── ProjectRepository.java
        │           │   ├── dao/
        │           │   │   ├── ActivityBlockDao.java
        │           │   │   ├── FileEventDao.java
        │           │   │   └── ProjectInfoDao.java
        │           │   └── migration/
        │           │       └── DatabaseMigration.java
        │           ├── model/
        │           │   ├── ActivityBlock.java
        │           │   ├── FileEvent.java
        │           │   └── ProjectInfo.java
        │           ├── service/
        │           │   ├── ProjectService.java
        │           │   ├── StatisticsService.java
        │           │   ├── TimelineService.java
        │           │   └── impl/
        │           │       └── TimelineServiceImpl.java
        │           ├── timeline/
        │           │   ├── ActivityBlockGenerator.java
        │           │   ├── AggregationContext.java
        │           │   └── MergeConfig.java
        │           ├── ui/
        │           │   ├── controller/
        │           │   │   └── MainController.java
        │           │   └── view/
        │           │       ├── ActivityBlockCell.java
        │           │       ├── FileDetailCell.java
        │           │       └── TimelineView.java
        │           └── util/
        │               ├── Config.java
        │               └── LogUtil.java
        └── resources/
            ├── css/
            │   └── style.css
            ├── fxml/
            │   └── main.fxml
            └── sql/
                └── schema.sql
```

**废弃文件（已由 Repository 替代，可删除）：**
- `database/dao/ActivityBlockDao.java`
- `database/dao/FileEventDao.java`
- `database/dao/ProjectInfoDao.java`
- `ui/view/TimelineView.java`

---

## 2. 数据库 DDL

```sql
-- WorkTrace Database Schema
-- SQLite 3.x

PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

-- 文件事件表：记录每一次文件系统变化
CREATE TABLE IF NOT EXISTS file_event (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,               -- CREATE / MODIFY / DELETE
    path        TEXT    NOT NULL,               -- 完整路径
    file_name   TEXT    NOT NULL,               -- 文件名
    extension   TEXT    DEFAULT '',             -- 扩展名(不含点)
    size        INTEGER DEFAULT 0,             -- 文件大小(字节)
    event_time  TEXT    NOT NULL                -- ISO-8601 时间戳
);

CREATE INDEX IF NOT EXISTS idx_file_event_time     ON file_event(event_time);
CREATE INDEX IF NOT EXISTS idx_file_event_ext      ON file_event(extension);
CREATE INDEX IF NOT EXISTS idx_file_event_type     ON file_event(event_type);

-- 活动块表：由事件聚合而成的时间段
CREATE TABLE IF NOT EXISTS activity_block (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    start_time  TEXT    NOT NULL,               -- ISO-8601
    end_time    TEXT    NOT NULL,               -- ISO-8601
    category    TEXT    DEFAULT 'OTHER',        -- CODE / DOCUMENT / IMAGE / VIDEO / CONFIG / OTHER
    summary     TEXT    DEFAULT ''              -- 人工或 AI 生成的摘要
);

CREATE INDEX IF NOT EXISTS idx_activity_block_time ON activity_block(start_time, end_time);

-- 防重复：同一时间段 + 同一类别只能有一条记录
CREATE UNIQUE INDEX IF NOT EXISTS idx_activity_block_dedup
    ON activity_block(start_time, end_time, category);

-- 项目信息表：自动识别或手动配置的项目根目录
CREATE TABLE IF NOT EXISTS project_info (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    project_name TEXT   NOT NULL,               -- 项目名称
    root_path   TEXT    NOT NULL UNIQUE          -- 项目根目录(绝对路径)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_root ON project_info(root_path);

-- 版本迁移表
CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**数据库位置：** `~/.worktrace/worktrace.db`（自动创建）

**迁移版本：**
- V1：初始 Schema
- V2：`activity_block` 增加 `idx_activity_block_dedup` UNIQUE 索引

---

## 3. EventAggregator 源码

```java
package com.worktrace.collector;

import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.timeline.ActivityBlockGenerator;
import com.worktrace.timeline.MergeConfig;
import com.worktrace.util.LogUtil;

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
```

---

## 4. ActivityBlockGenerator 源码

```java
package com.worktrace.timeline;

import com.worktrace.collector.CategoryClassifier;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.util.LogUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间线聚合引擎 —— 系统核心模块。
 *
 * 将大量原始 FileEvent 聚合为少量有意义的 ActivityBlock。
 *
 * 算法: 单遍扫描 + 滑动窗口
 * ─────────────────────────────────────────────────────────
 *   输入: events[] (按 eventTime 排序)
 *   输出: blocks[]
 *
 *   block = null
 *   for each event in events:
 *       if block == null:
 *           block = newContext(event)
 *       else if shouldMerge(block, event):
 *           block.add(event)
 *       else:
 *           emit(block)
 *           block = newContext(event)
 *   emit(block)
 * ─────────────────────────────────────────────────────────
 *
 * 合并规则(按优先级):
 *   1. 时间间隔 > maxGapMinutes → 强制分裂
 *   2. 同项目(路径前缀匹配)     → 优先合并
 *   3. 同文件类别               → 优先合并
 *   4. 以上都不满足             → 分裂
 *
 * 分类体系:
 *   CODE     → 代码文件(java, py, js, ts, go, rs, ...)
 *   DOCUMENT → 文档文件(doc, pdf, md, txt, xlsx, ...)
 *   IMAGE    → 图片文件(png, jpg, svg, psd, ...)
 *   VIDEO    → 音视频文件(mp4, mp3, avi, ...)
 *   CONFIG   → 配置文件(json, yaml, xml, properties, ...)
 *   OTHER    → 其余
 */
public class ActivityBlockGenerator {

    private final CategoryClassifier classifier;
    private final MergeConfig config;

    public ActivityBlockGenerator() {
        this.classifier = new CategoryClassifier();
        this.config     = MergeConfig.DEFAULT;
    }

    public ActivityBlockGenerator(MergeConfig config) {
        this.classifier = new CategoryClassifier();
        this.config     = config;
    }

    public ActivityBlockGenerator(CategoryClassifier classifier, MergeConfig config) {
        this.classifier = classifier;
        this.config     = config;
    }

    /**
     * 将文件事件列表聚合为活动块列表。
     */
    public List<ActivityBlock> generate(List<FileEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // 1. 按时间排序
        List<FileEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(FileEvent::getEventTime));

        // 2. 单遍扫描聚合
        List<ActivityBlock> blocks = new ArrayList<>();
        AggregationContext ctx = null;

        for (FileEvent event : sorted) {
            if (ctx == null) {
                ctx = new AggregationContext(classifier, event);
            } else if (ctx.shouldMerge(event, config)) {
                ctx.add(event);
            } else {
                blocks.add(ctx.toActivityBlock());
                ctx = new AggregationContext(classifier, event);
            }
        }

        if (ctx != null) {
            blocks.add(ctx.toActivityBlock());
        }

        LogUtil.info("聚合完成: " + sorted.size() + " 个事件 → " + blocks.size() + " 个活动块");
        return blocks;
    }

    /**
     * 增量聚合 —— 将新事件追加到已有的活动块列表中。
     */
    public List<ActivityBlock> generateIncremental(
            List<ActivityBlock> existingBlocks, List<FileEvent> newEvents) {

        if (newEvents == null || newEvents.isEmpty()) {
            return existingBlocks;
        }

        List<FileEvent> sorted = new ArrayList<>(newEvents);
        sorted.sort(Comparator.comparing(FileEvent::getEventTime));

        List<ActivityBlock> result = new ArrayList<>(existingBlocks);

        for (FileEvent event : sorted) {
            if (result.isEmpty()) {
                return generate(
                    new ArrayList<>() {{ addAll(existingBlocks.stream()
                        .flatMap(b -> toEvents(b).stream()).toList());
                        addAll(sorted);
                    }}
                );
            }

            ActivityBlock lastBlock = result.get(result.size() - 1);
            AggregationContext ctx = fromBlock(lastBlock);

            if (ctx.shouldMerge(event, config)) {
                ctx.add(event);
                result.set(result.size() - 1, ctx.toActivityBlock());
            } else {
                AggregationContext newCtx = new AggregationContext(classifier, event);
                result.add(newCtx.toActivityBlock());
            }
        }

        return result;
    }

    private AggregationContext fromBlock(ActivityBlock block) {
        FileEvent pseudo = new FileEvent();
        pseudo.setEventTime(block.getStartTime());
        pseudo.setEventType("MODIFY");
        pseudo.setPath("");
        pseudo.setFileName("");
        pseudo.setExtension("");
        pseudo.setSize(0);
        AggregationContext ctx = new AggregationContext(classifier, pseudo);
        return ctx;
    }

    private List<FileEvent> toEvents(ActivityBlock block) {
        FileEvent e = new FileEvent();
        e.setEventTime(block.getStartTime());
        e.setEventType("MODIFY");
        e.setPath("");
        e.setFileName("");
        e.setExtension("");
        e.setSize(0);
        return List.of(e);
    }
}
```

---

## 5. TimelineServiceImpl 源码

```java
package com.worktrace.service.impl;

import com.worktrace.database.ActivityRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.service.TimelineService;
import com.worktrace.util.LogUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * TimelineService 的标准实现。
 *
 * 调用关系：
 *   MainController → TimelineService(接口) → TimelineServiceImpl → ActivityRepository → SQLite
 */
public class TimelineServiceImpl implements TimelineService {

    private final ActivityRepository activityRepository;

    public TimelineServiceImpl(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public List<ActivityBlock> getDailyTimeline(LocalDate date) {
        try {
            List<ActivityBlock> blocks = activityRepository.findByDate(date);
            LogUtil.info("查询时间线: " + date + " → " + blocks.size() + " 个活动块");
            return blocks;
        } catch (Exception e) {
            LogUtil.error("查询时间线失败: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Long> getCategoryDuration(LocalDate date) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (Category cat : Category.values()) {
            result.put(cat.key, 0L);
        }

        try {
            List<ActivityRepository.CategoryDuration> durations =
                activityRepository.durationByCategory(date);

            for (ActivityRepository.CategoryDuration cd : durations) {
                result.merge(cd.category(), cd.minutes(), Long::sum);
            }
        } catch (Exception e) {
            LogUtil.error("统计类别时长失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public long getTotalActiveMinutes(LocalDate date) {
        try {
            long total = activityRepository.totalDurationByDate(date);
            LogUtil.info("总活动时长: " + date + " → " + total + " 分钟");
            return total;
        } catch (Exception e) {
            LogUtil.error("查询总活动时长失败: " + e.getMessage());
            return 0;
        }
    }

    private enum Category {
        CODE("CODE"),
        DOCUMENT("DOCUMENT"),
        IMAGE("IMAGE"),
        VIDEO("VIDEO"),
        CONFIG("CONFIG"),
        OTHER("OTHER");

        final String key;
        Category(String key) { this.key = key; }
    }
}
```

---

## 6. MainController 源码

```java
package com.worktrace.ui.controller;

import com.worktrace.collector.FileWatcherService;
import com.worktrace.database.FileEventRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.FileEvent;
import com.worktrace.service.TimelineService;
import com.worktrace.ui.view.ActivityBlockCell;
import com.worktrace.ui.view.FileDetailCell;

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
 * 刷新机制：
 *   - 启动时立即加载一次
 *   - WatchService 运行时，每 30 秒自动刷新
 *   - 使用 JavaFX Timeline 在 UI 线程上调度
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
    private Runnable onStopCallback;

    // ---------- 状态 ----------
    private final ObservableList<ActivityBlock> blockData = FXCollections.observableArrayList();
    private final ObservableList<String> fileDetailData = FXCollections.observableArrayList();
    private Button activeNavButton;
    private Timeline autoRefreshTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTimelineList();
        initDetailFileList();
        initNavigation();
        initWatchToggle();
        lblTimelineDate.setText(LocalDate.now().format(DATE_FMT));
    }

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

    private void loadAllData() {
        if (timelineService == null) return;

        LocalDate today = LocalDate.now();

        List<ActivityBlock> blocks    = timelineService.getDailyTimeline(today);
        long totalMinutes             = timelineService.getTotalActiveMinutes(today);
        Map<String, Long> catDuration = timelineService.getCategoryDuration(today);

        blockData.setAll(blocks);
        if (!blocks.isEmpty()) {
            timelineList.getSelectionModel().selectFirst();
        }

        lblBlockCount.setText(String.valueOf(blocks.size()));
        lblActiveMinutes.setText(String.valueOf(totalMinutes));

        lblCatCode.setText(catDuration.getOrDefault("CODE", 0L) + " 分钟");
        lblCatDocument.setText(catDuration.getOrDefault("DOCUMENT", 0L) + " 分钟");
        lblCatImage.setText(catDuration.getOrDefault("IMAGE", 0L) + " 分钟");
        lblCatVideo.setText(catDuration.getOrDefault("VIDEO", 0L) + " 分钟");
        lblCatConfig.setText(catDuration.getOrDefault("CONFIG", 0L) + " 分钟");

        lblEventCount.setText("今日: " + blocks.size() + " 块 / " + totalMinutes + " 分钟");
        lblStatusText.setText("已加载 " + blocks.size() + " 个活动块 · " + today.format(DATE_FMT));
    }

    private void showBlockDetail(ActivityBlock block) {
        lblDetailTitle.setText(block.getSummary());
        lblDetailTime.setText(
            block.getStartTime().format(TIME_FMT) + " - " +
            block.getEndTime().format(TIME_FMT)
        );

        long minutes = java.time.Duration.between(block.getStartTime(), block.getEndTime()).toMinutes();
        lblDetailDuration.setText(String.valueOf(Math.max(minutes, 1)));

        lblDetailTitle.getStyleClass().removeIf(s -> s.startsWith("card-category-"));
        lblDetailTitle.getStyleClass().add("card-category-" + block.getCategory().toLowerCase());

        fileDetailData.clear();
        if (fileEventRepository != null) {
            try {
                List<FileEvent> files = fileEventRepository.findByTimeRange(
                    block.getStartTime(), block.getEndTime().plusSeconds(1)
                );
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

    private void switchView(String view, Button source) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
        }
        source.getStyleClass().add("nav-button-active");
        activeNavButton = source;

        lblTimelineTitle.setText(switch (view) {
            case "overview" -> "今日时间线";
            case "timeline" -> "全部时间线";
            case "projects" -> "项目统计";
            default -> "时间线";
        });

        lblStatusText.setText("切换到: " + source.getText().trim());
    }

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
        Platform.runLater(this::loadAllData);
    }

    public void setFileEventRepository(FileEventRepository repo) {
        this.fileEventRepository = repo;
    }

    public void setOnStopCallback(Runnable callback) {
        this.onStopCallback = callback;
    }
}
```

---

## 7. 当前存在的 TODO

### 代码中的 TODO 注释

| 文件 | 行号 | 内容 | 状态 |
|------|------|------|------|
| `database/dao/ActivityBlockDao.java` | 47 | `// TODO: 实现按日期查询活动块` | 已由 `ActivityRepository.findByDate()` 实现，此文件为废弃文件 |
| `database/dao/ProjectInfoDao.java` | 44 | `// TODO: 实现查询全部项目` | 已由 `ProjectRepository.findAll()` 实现，此文件为废弃文件 |
| `database/dao/ProjectInfoDao.java` | 49 | `// TODO: 实现按路径查找` | 已由 `ProjectRepository.findByRootPath()` 实现，此文件为废弃文件 |
| `ui/view/TimelineView.java` | 39 | `// TODO: 实现活动块的可视化节点` | 未使用，已被 `ActivityBlockCell` 替代 |

### 功能层面的 TODO

| 优先级 | TODO | 说明 |
|--------|------|------|
| P0 | 清理废弃 DAO 文件 | `dao/` 目录下 3 个文件已被 Repository 替代，应删除 |
| P0 | 清理废弃 TimelineView | 已被 ActivityBlockCell 替代，应删除 |
| P1 | 实现 ProjectService | 接口已定义，实现类未创建 |
| P1 | 实现 StatisticsService | 接口已定义，实现类未创建 |
| P1 | 事件去抖动 (Debounce) | IDE 保存文件触发 3-5 次 MODIFY，应合并 |
| P1 | 异步写入队列 | WatchService 线程同步写 DB，高频场景会阻塞 |
| P2 | 项目自动识别 | 基于 .git / pom.xml / package.json 自动注册项目 |
| P2 | 时间线按日切换 | 日期选择器 + 历史查看 |
| P2 | 项目统计视图 | 各项目活动时长饼图 |
| P2 | 系统托盘 | 最小化到托盘，后台运行 |
| P3 | AI 日报生成 | 基于活动块生成每日工作总结 |
| P3 | 数据清理策略 | file_event 表只增不删，需 TTL 机制 |
| P3 | 单元测试 | 核心模块 0 测试覆盖 |
