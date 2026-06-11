# WorkTrace 项目审计报告

**审计日期：** 2026-06-11
**审计人：** Claude (AI 架构师)
**审计版本：** v0.1.0-SNAPSHOT
**分支：** dev

---

# 1. 项目概览

**项目名称：** WorkTrace

**项目目标：** 自动记录 Windows 电脑中的文件变化行为，将大量文件系统事件聚合为用户可理解的工作时间线。最终目标是成为个人工作轨迹记录与回顾工具，后续扩展 AI 日报生成能力。

**当前开发阶段：** MVP 第一阶段（基础框架 + 核心功能原型）

**已完成功能：**

| 编号 | 功能 | 状态 | 说明 |
|------|------|------|------|
| F1 | Maven 项目结构 | ✅ 完成 | Java 21 + JPMS 模块化 |
| F2 | SQLite 数据库设计 | ✅ 完成 | 3 张核心表 + 迁移机制 |
| F3 | 文件监听 (WatchService) | ✅ 完成 | 递归监听、多目录、噪声过滤 |
| F4 | SQLite 持久化层 | ✅ 完成 | 3 个 Repository + 分页 + 批量插入 |
| F5 | 时间线聚合引擎 | ✅ 完成 | 单遍扫描算法、5 类分类 |
| F6 | JavaFX 主界面 | ✅ 完成 | 三栏布局、暗色主题、卡片时间线 |
| F7 | 活动详情面板 | ✅ 完成 | 点击联动、文件列表展示 |

**未完成功能：**

| 编号 | 功能 | 说明 |
|------|------|------|
| N1 | 实时数据联动 | UI 使用演示数据，未接入真实 Repository 查询 |
| N2 | TimelineService 实现 | 接口已定义，实现类未创建 |
| N3 | ProjectService 实现 | 接口已定义，实现类未创建 |
| N4 | StatisticsService 实现 | 接口已定义，实现类未创建 |
| N5 | 事件去抖动 (Debounce) | 同一文件短时间多次修改会产生多条事件 |
| N6 | 活动块持久化 | 聚合结果未写入 activity_block 表 |
| N7 | 项目自动识别 | ProjectRepository.findProjectForPath 已实现但未接入 |
| N8 | 配置文件热加载 | Config 仅在启动时读取一次 |
| N9 | 系统托盘支持 | 桌面应用最小化到托盘 |
| N10 | AI 日报生成 | 规划中 |

---

# 2. 项目目录结构

```
WorkTrace/
├── .git/
├── .gitignore
├── pom.xml
├── README.md
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
        │           │   └── TimelineService.java
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

**包结构职责：**

| 包 | 职责 | 文件数 |
|---|---|---|
| `app` | 应用入口，启动流程编排 | 1 |
| `collector` | 文件事件采集层 (WatchService + 聚合) | 4 |
| `database` | 持久化层 (Repository + 迁移 + 分页) | 7 (含 3 个废弃 DAO) |
| `model` | 实体类 | 3 |
| `service` | 业务服务接口 (未实现) | 3 |
| `timeline` | 时间线聚合引擎核心 | 3 |
| `ui.controller` | JavaFX 控制器 | 1 |
| `ui.view` | 自定义视图组件 | 3 |
| `util` | 工具类 | 2 |

---

# 3. 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (LTS) | 语言，使用 JPMS 模块化、record、text block、switch expression |
| JavaFX | 21.0.2 | GUI 框架，FXML + CSS |
| SQLite | 3.45.1.0 (JDBC) | 嵌入式数据库，WAL 模式 |
| Maven | 3.6.3 | 构建工具 |
| SLF4J | 2.0.12 | 日志门面 (当前用 slf4j-simple 实现) |

**Maven 依赖清单：**

```xml
<!-- JavaFX UI -->
org.openjfx:javafx-controls:21.0.2
org.openjfx:javafx-fxml:21.0.2

<!-- SQLite JDBC 驱动 -->
org.xerial:sqlite-jdbc:3.45.1.0

<!-- 日志 -->
org.slf4j:slf4j-api:2.0.12
org.slf4j:slf4j-simple:2.0.12
```

**构建插件：**

```xml
<!-- 编译器 -->
org.apache.maven.plugins:maven-compiler-plugin:3.12.1

<!-- JavaFX 运行 -->
org.openjfx:javafx-maven-plugin:0.0.8
```

**无第三方 UI 库、无 ORM 框架、无依赖注入框架。** 全部手写。

---

# 4. 数据库设计

**数据库位置：** `~/.worktrace/worktrace.db` (自动创建)
**日志模式：** WAL (Write-Ahead Logging)

## 4.1 file_event 表

```sql
CREATE TABLE IF NOT EXISTS file_event (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,               -- CREATE / MODIFY / DELETE
    path        TEXT    NOT NULL,               -- 完整路径
    file_name   TEXT    NOT NULL,               -- 文件名
    extension   TEXT    DEFAULT '',             -- 扩展名(不含点)
    size        INTEGER DEFAULT 0,             -- 文件大小(字节)
    event_time  TEXT    NOT NULL                -- ISO-8601 时间戳
);

CREATE INDEX idx_file_event_time  ON file_event(event_time);
CREATE INDEX idx_file_event_ext   ON file_event(extension);
CREATE INDEX idx_file_event_type  ON file_event(event_type);
```

**对应实体：** `FileEvent.java`
**对应仓库：** `FileEventRepository.java`
**记录来源：** FileWatcherServiceImpl → listener → FileEventRepository.insert()

## 4.2 activity_block 表

```sql
CREATE TABLE IF NOT EXISTS activity_block (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    start_time  TEXT    NOT NULL,               -- ISO-8601
    end_time    TEXT    NOT NULL,               -- ISO-8601
    category    TEXT    DEFAULT 'OTHER',        -- CODE/DOCUMENT/IMAGE/VIDEO/CONFIG/OTHER
    summary     TEXT    DEFAULT ''              -- 人工或 AI 生成的摘要
);

CREATE INDEX idx_activity_block_time ON activity_block(start_time, end_time);
```

**对应实体：** `ActivityBlock.java`
**对应仓库：** `ActivityRepository.java`
**记录来源：** ActivityBlockGenerator → ActivityRepository.insert() (尚未接入)

## 4.3 project_info 表

```sql
CREATE TABLE IF NOT EXISTS project_info (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    project_name TEXT   NOT NULL,
    root_path   TEXT    NOT NULL UNIQUE
);

CREATE UNIQUE INDEX idx_project_root ON project_info(root_path);
```

**对应实体：** `ProjectInfo.java`
**对应仓库：** `ProjectRepository.java`

## 4.4 schema_version 表

```sql
CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**用途：** DatabaseMigration 版本控制

## 4.5 实体关系

```
file_event ──(聚合)──→ activity_block
                          │
                          ▼
                    project_info (通过 path 前缀匹配)
```

当前三张表之间**无外键约束**，通过路径前缀在应用层关联。

---

# 5. 核心模块说明

## 5.1 FileWatcherService / FileWatcherServiceImpl

**职责：** 基于 NIO WatchService 监听指定目录的文件变化事件。

**输入：**
- 监听目录列表 (来自 Config.watch.dirs)
- 文件变化事件 (ENTRY_CREATE / ENTRY_MODIFY / ENTRY_DELETE)

**输出：**
- 通过 `FileEventListener.onFileEvent(eventType, filePath, size)` 回调通知

**核心逻辑：**
1. 单守护线程 `worktrace-watcher` 执行 `WatchService.take()` 阻塞轮询
2. 收到事件后过滤噪声目录 (.git, node_modules, target 等)
3. ENTRY_CREATE + 目录 → 递归 `registerTree()` 注册子目录
4. 文件事件 → 通知所有已注册的 listener

**调用关系：**
```
WorkTraceApp.start()
    → new FileWatcherServiceImpl()
    → watcherService.watchDirectories(configuredDirs)
    → watcherService.start()
        → pollLoop() [daemon thread]
            → handleEvent()
                → listener.onFileEvent()  ← 同步回调
```

## 5.2 DatabaseManager

**职责：** SQLite 连接管理器(单例)，负责数据库创建、Schema 初始化、WAL 模式设置。

**输入：** 无构造参数，通过 `getInstance()` 获取

**输出：** `Connection` 对象

**核心逻辑：**
1. `~/.worktrace/` 目录不存在则创建
2. `DriverManager.getConnection()` 打开 SQLite 连接
3. 执行 `PRAGMA journal_mode=WAL` + `PRAGMA foreign_keys=ON`
4. 读取 `schema.sql` 资源文件并逐条执行

**调用关系：**
```
WorkTraceApp.start()
    → DatabaseManager.getInstance().initialize()
        → runSchemaScript()
    → new FileEventRepository()  // 内部调用 getConnection()
```

## 5.3 Repository 层 (FileEventRepository / ActivityRepository / ProjectRepository)

**职责：** 数据访问对象，封装所有 SQL 操作。

**输入：** Entity 对象 / 查询参数 (日期、分页、路径)

**输出：** Entity 对象 / `PageResult<T>` / 统计值

**核心逻辑 (以 FileEventRepository 为例)：**
- `insert()` — 单条 INSERT
- `batchInsert()` — 事务 + addBatch + executeBatch
- `findByDate()` — WHERE event_time BETWEEN
- `findRecentDays()` — WHERE event_time >= N天前
- `findByProject()` — WHERE path LIKE 'rootPath%'
- `findAll(page, pageSize)` — LIMIT OFFSET 分页
- `countGroupByExtension()` — GROUP BY 统计

**调用关系：**
```
FileWatcherServiceImpl → listener → FileEventRepository.insert()
MainController.refreshTimeline() → FileEventRepository.findToday()
```

## 5.4 ActivityBlockGenerator / AggregationContext / MergeConfig

**职责：** 时间线聚合引擎核心。将大量 FileEvent 聚合为少量 ActivityBlock。

**输入：** `List<FileEvent>` (可无序)

**输出：** `List<ActivityBlock>` (按时间正序)

**核心逻辑：** 见第 6 节详细说明

**调用关系：**
```
EventAggregator.onEvent()
    → buffer.add(event)
    → if buffer.size >= batchSize:
        → flush()
            → ActivityBlockGenerator.generate(buffer)
                → AggregationContext.shouldMerge()
                → AggregationContext.add()
                → AggregationContext.toActivityBlock()
```

## 5.5 TimelineService / ProjectService / StatisticsService

**职责：** 业务服务接口层。

**当前状态：** 仅有接口定义，无实现类。

**接口方法：**

```
TimelineService:
    getDailyTimeline(LocalDate) → List<ActivityBlock>
    getCategoryDuration(LocalDate) → Map<String, Long>
    getTotalActiveMinutes(LocalDate) → long

ProjectService:
    registerProject(name, rootPath) → long
    identifyProject(filePath) → Optional<ProjectInfo>
    listAll() → List<ProjectInfo>

StatisticsService:
    countByExtension(LocalDate) → Map<String, Long>
    durationByProject(LocalDate) → Map<String, Long>
    hourlyDistribution(LocalDate) → Map<Integer, Long>
    totalEventCount(LocalDate) → long
```

## 5.6 JavaFX UI (MainController + View)

**职责：** 用户界面，展示时间线和活动详情。

**布局结构：** BorderPane (FXML 驱动)
- TOP: 状态栏 (监听状态 + 目录 + 事件计数)
- LEFT: 侧边栏 (导航 + 今日概览)
- CENTER: 时间线列表 (ListView<ActivityBlock> + 自定义 Cell)
- RIGHT: 详情面板 (统计卡片 + 文件列表)
- BOTTOM: 状态文本

**核心逻辑：**
1. `initialize()` 初始化 ListView + CellFactory + 事件绑定
2. `loadDemoData()` 加载 5 条模拟数据 (开发阶段)
3. 点击时间线卡片 → `showBlockDetail()` 更新右侧详情
4. 导航按钮 → `switchView()` 切换标题和样式

**调用关系：**
```
FXMLLoader.load()
    → MainController.initialize()
        → initTimelineList()  // 设置 CellFactory
        → initDetailFileList()
        → initNavigation()    // 按钮事件绑定
        → loadDemoData()      // 临时演示数据
```

---

# 6. 时间线聚合算法

## 6.1 聚合规则

| 优先级 | 规则 | 条件 | 结果 |
|--------|------|------|------|
| 1 | 时间窗口 | gap > maxGapMinutes (默认 15) | 强制分裂 |
| 2 | 同项目 | 事件路径以已知项目目录为前缀 | 优先合并 |
| 3 | 同类别 | 文件扩展名分类相同 | 优先合并 |
| 4 | 默认 | 以上都不满足 | 分裂 |

## 6.2 时间窗口

- 默认阈值：15 分钟
- 可配置：`MergeConfig.RELAXED` (30 分钟) / `MergeConfig.STRICT` (5 分钟)
- 判断方式：`Duration.between(ctx.endTime, event.eventTime).toMinutes()`

## 6.3 项目识别逻辑

```java
// AggregationContext.containsProject()
String eventDir = extractDirectory(event.getPath());
for (String projectPath : projectPaths) {
    if (eventDir.startsWith(projectPath) || projectPath.startsWith(eventDir)) {
        return true;
    }
}
```

当前使用**目录路径前缀匹配**，未使用 `project_info` 表。这是一个已知的缺陷。

## 6.4 分类逻辑

```
扩展名 → CategoryClassifier.classify() → 类别

.java   → CODE
.py     → CODE
.docx   → CODE
.pdf    → DOCUMENT
.png    → IMAGE
.mp4    → VIDEO
.json   → CONFIG
.xyz    → OTHER
```

共支持 40+ 种扩展名映射。

## 6.5 活动块生成逻辑

**AggregationContext 状态模型：**

```
newContext(event)
    │
    ├─ startTime = event.time
    ├─ endTime = event.time
    ├─ files = [event]
    ├─ categoryCounts = {CODE: 1}
    └─ projectPaths = {"/src/main/java"}
    │
    ▼ add(event2)  ──→  更新时间、文件去重、计数累加
    │
    ▼ toActivityBlock()
    │
    ├─ category = categoryCounts 中最多的
    ├─ summary = buildSummary()  // "代码开发 · A.java 等5个文件"
    └─ return ActivityBlock
```

## 6.6 伪代码

```
function generate(events):
    sort events by eventTime ascending
    blocks = []
    ctx = null

    for each event in events:
        if ctx is null:
            ctx = new AggregationContext(event)
        else if ctx.shouldMerge(event, config):
            ctx.add(event)
        else:
            blocks.add(ctx.toActivityBlock())
            ctx = new AggregationContext(event)

    if ctx is not null:
        blocks.add(ctx.toActivityBlock())

    return blocks

function shouldMerge(ctx, event):
    gap = event.time - ctx.endTime
    if gap > config.maxGapMinutes:
        return false
    if config.projectPriority AND ctx.containsProject(event):
        return true
    if config.categoryPriority AND ctx.isSameCategory(event):
        return true
    return false

function add(ctx, event):
    ctx.startTime = min(ctx.startTime, event.time)
    ctx.endTime   = max(ctx.endTime, event.time)
    if event.path in ctx.files:
        replace existing entry
    else:
        ctx.files.append(event)
    ctx.categoryCounts[event.category]++
    ctx.projectPaths.add(extractDirectory(event.path))
```

**时间复杂度：** O(N log N) (排序) + O(N × M) (扫描，M = 当前块文件数)
**空间复杂度：** O(N) (排序副本) + O(B × M) (B = 块数)

---

# 7. 当前界面截图说明

## 7.1 首页结构

三栏 BorderPane 布局，暗色主题 (IntelliJ IDEA 风格)：

```
┌──────────────────────────────────────────────────────────────────┐
│  ● 监听中  │  监听目录: Desktop, Documents    │  今日事件: 18   │  ← 顶部
├────────────┼─────────────────────────────────┼──────────────────┤
│            │                                 │                  │
│  工作轨迹   │  今日时间线          2026-06-11 │  活动详情        │
│            │  ┌────────────────────────────┐ │                  │
│  ┌───────┐ │  │ 09:01-09:35  [代码] 34分钟 │ │  09:01 - 09:35  │
│  │今日活动│ │  │ 代码开发·App.java 等5文件  │ │                  │
│  │  0 块  │ │  ├────────────────────────────┤ │  ┌───┐ ┌───┐   │
│  │  0 文件│ │  │ 10:05-10:20  [文档] 15分钟 │ │  │ 34│ │ 5 │   │
│  └───────┘ │  │ 文档编辑·README.md         │ │  │分钟│ │文件│   │
│            │  ├────────────────────────────┤ │  └───┘ └───┘   │
│  导航       │  │ 10:30-10:50  [配置] 20分钟 │ │                  │
│  📊 今日概览│  │ 配置修改·pom.xml 等3文件    │ │  涉及文件       │
│  📋 时间线  │  └────────────────────────────┘ │  ☕ MainCtrl..  │
│  📁 项目统计│                                 │  ☕ App.java    │
│            │                                 │                  │
│  ▶ 开始监听│                                 │                  │
├────────────┴─────────────────────────────────┴──────────────────┤
│  就绪                                                          │  ← 底部
└──────────────────────────────────────────────────────────────────┘
```

## 7.2 左侧菜单

- **今日概览**：显示今日活动块数 + 文件数统计卡片
- **时间线** / **项目统计**：导航按钮 (切换标题，未实现视图切换)
- **开始/停止监听**：控制 FileWatcherService 启停

## 7.3 中间时间线

- `ListView<ActivityBlock>` + 自定义 `ActivityBlockCell`
- 每张卡片显示：时间范围、类别标签、摘要、时长
- 类别色彩：CODE 绿、DOCUMENT 蓝、IMAGE 橙、VIDEO 紫、CONFIG 黄
- 点击卡片联动右侧详情

## 7.4 右侧详情

- 统计卡片：时长(分钟) + 文件数
- 文件列表：自定义 `FileDetailCell`，显示文件名 + 路径 + 图标
- 当前使用**模拟数据** (simulateFileList)

## 7.5 已实现功能

- [x] 暗色主题完整 CSS (305 行)
- [x] FXML 三栏布局
- [x] 时间线卡片自定义单元格
- [x] 文件详情自定义单元格
- [x] 导航按钮样式切换
- [x] 监听状态指示器
- [x] 点击联动详情面板
- [x] 演示数据加载

## 7.6 未实现功能

- [ ] 真实数据接入 (Repository → Service → Controller)
- [ ] 时间线按日期切换
- [ ] 项目统计视图
- [ ] 搜索/过滤
- [ ] 时间线缩放
- [ ] 数据导出
- [ ] 系统托盘

---

# 8. 项目运行手册

## 8.1 JDK 安装要求

```
JDK: Oracle JDK 21.0.11 或 OpenJDK 21+
验证: java -version  →  "21.0.x"
```

**注意：** 系统 PATH 中的 `java` 必须是 21+。Maven 的 `JAVA_HOME` 也必须指向 JDK 21。

## 8.2 Maven 安装

```
Maven: 3.6.3+
验证: mvn -version
```

## 8.3 启动方式

```bash
# 方式一：Maven 命令行
cd e:/opc/WorkTrace
mvn clean javafx:run

# 方式二：IDEA
# 右侧 Maven → Plugins → javafx → javafx:run

# 方式三：直接运行主类
# 运行 com.worktrace.app.WorkTraceApp (需要配置模块路径)
```

**如果 Maven 使用 Java 17：**

```bash
# 设置 JAVA_HOME 后运行
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
mvn clean javafx:run
```

或在 IDEA 中设置 Maven 运行配置的 JRE 为 JDK 21。

## 8.4 数据库位置

```
~/.worktrace/worktrace.db
即: C:\Users\<用户名>\.worktrace\worktrace.db
```

自动创建，无需手动初始化。

## 8.5 监听目录配置

```
~/.worktrace/config.properties
```

```properties
# 分号分隔多个目录
watch.dirs=C:\Users\xxx\Desktop;C:\Users\xxx\Documents

# 聚合时间窗口(分钟)
aggregate.gap.minutes=15
```

**默认监听：** Desktop + Documents

## 8.6 运行步骤

```
1. 克隆仓库
   git clone https://github.com/libaolong4473/WorkTrace.git

2. 进入项目目录
   cd WorkTrace

3. 编译
   mvn clean compile

4. 运行
   mvn javafx:run

5. 观察
   - 左侧显示今日概览
   - 中间显示时间线(当前为演示数据)
   - 点击卡片查看详情
```

## 8.7 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `Unsupported major.minor version 65.0` | Maven 用 Java 17 编译 | 设置 JAVA_HOME 为 JDK 21 |
| `Unable to coerce Insets` | FXML padding 语法错误 | 已修复，确保使用最新代码 |
| `ClassNotFoundException` | 模块路径问题 | 确保使用 javafx:run 而非直接运行 |
| 中文乱码 | 控制台编码不是 UTF-8 | 设置 `-Dfile.encoding=UTF-8` |
| WatchService 不触发 | Windows 权限不足 | 以管理员身份运行 |

---

# 9. 当前存在的问题

## 9.1 架构问题

### P1: Service 层空壳 — 严重

`TimelineService`、`ProjectService`、`StatisticsService` 仅有接口，无实现类。`MainController` 使用 `loadDemoData()` 硬编码演示数据，**整个数据流是断裂的**：

```
FileWatcherServiceImpl → FileEventRepository.insert()  ← 数据写入了
                                                            ↓
MainController ← loadDemoData()  ← 但 UI 从不读取数据库
```

**影响：** 应用重启后所有数据丢失（UI 看不到历史数据）。

### P2: Repository 直接暴露 Connection — 中等

Repository 构造函数直接获取 `DatabaseManager.getConnection()` 的单例 Connection。这意味着：
- 所有 Repository 共享同一个 Connection
- 在 WatchService 线程上调用 `insert()` 时，如果 UI 线程同时查询，会产生 `SQLITE_BUSY`
- 虽然 WAL 模式缓解了读写冲突，但单 Connection 模式仍是隐患

**建议：** 引入连接池或每次操作获取新 Connection。

### P3: 旧 DAO 类未清理 — 低

`database/dao/` 目录下仍有 `FileEventDao.java`、`ActivityBlockDao.java`、`ProjectInfoDao.java`，已被新的 Repository 替代但未删除。造成代码冗余和混淆。

### P4: 事件链路未闭合 — 严重

FileWatcherServiceImpl 的 listener 在 WatchService 线程上同步调用 `FileEventRepository.insert()`。但 `ActivityBlockGenerator` 从未被调用。`EventAggregator` 虽然接入了 `ActivityBlockGenerator`，但 `WorkTraceApp` 中从未使用 `EventAggregator`。

**实际事件流：**
```
WatchService → listener → FileEventRepository.insert() → 结束
                                           ↑
                              ActivityBlockGenerator 从未被调用
                              EventAggregator 从未被使用
```

## 9.2 性能问题

### P5: WatchService 线程同步写数据库 — 高

每次文件事件都同步执行 `FileEventRepository.insert()`，包含 SQL 执行。高频文件操作（如 `npm install` 产生数千文件事件）会阻塞 WatchService 轮询线程，导致事件丢失。

**建议：** 使用 `ArrayBlockingQueue` 做生产者-消费者解耦，独立写入线程批量写入。

### P6: 无事件去抖动 (Debounce) — 高

IDE 保存文件时可能触发 3-5 次 MODIFY 事件（buffer write, flush, metadata update）。当前无去抖动，会产生大量重复事件。

**建议：** 在 `handleEvent()` 中增加 500ms 去抖动，同路径同类型事件合并。

### P7: registerTree 在 WatchService 线程上执行 — 中

`Files.walkFileTree()` 遍历大目录树（如 node_modules 未被过滤时）会阻塞事件轮询。虽然已过滤大部分噪声目录，但首次注册大量目录时仍可能阻塞数秒。

### P8: AggregationContext 文件去重用 List 遍历 — 低

`add()` 方法中按路径去重使用 `for` 循环遍历 `files` 列表，时间复杂度 O(M)。当单个活动块包含大量文件时（如 100+），性能下降。

**建议：** 增加 `Map<String, Integer>` 索引。

## 9.3 数据库问题

### P9: event_time 存储为 TEXT 而非 INTEGER — 中

SQLite 中 `event_time` 用 `TEXT` 存储 ISO-8601 字符串。这导致：
- 时间比较依赖字符串字典序（虽然 ISO-8601 格式可以正确排序）
- `julianday()` 函数需要类型转换
- 占用更多存储空间

**建议：** 存储为 Unix 时间戳 (INTEGER)，查询时转换。

### P10: 无数据清理机制 — 中

file_event 表只增不删。假设每天产生 5000 条事件，一年将累积 180 万条记录。当前无 TTL、无归档、无清理策略。

### P11: 无事务管理封装 — 低

`batchInsert()` 手动管理 `setAutoCommit/commit/rollback`。如果中间出现异常且 rollback 失败，连接状态不可预测。

## 9.4 JavaFX 问题

### P12: 演示数据硬编码 — 严重

`MainController.loadDemoData()` 创建 5 条硬编码的 `ActivityBlock`。这是开发阶段的临时方案，但当前已成为唯一的 UI 数据源。真实数据从未流入 UI。

### P13: 无线程安全保护 — 中

`refreshTimeline()` 使用 `Platform.runLater()` 更新 UI，这是正确的。但 `FileWatcherService` 的 listener 在 WatchService 线程上调用 `FileEventRepository.insert()`，然后如果后续需要通知 UI 更新，需要通过 `Platform.runLater()` 桥接。当前这条链路未建立。

### P14: TimelineView 未被使用 — 低

`ui/view/TimelineView.java` 是一个独立的 VBox 子类，但在 FXML 和 Controller 中均未使用。当前时间线使用 `ListView<ActivityBlock>` + `ActivityBlockCell` 实现。`TimelineView` 是早期设计的遗留物。

## 9.5 WatchService 问题

### P15: Windows ReadDirectoryChangesW 的已知限制

WatchService 在 Windows 上底层使用 `ReadDirectoryChangesW`：
- 无法监听网络驱动器
- 某些杀毒软件会干扰事件
- FAT32 分区不支持
- 事件可能延迟或丢失

这些是 JDK 的限制，无法在应用层解决。

### P16: 未处理文件重命名

Windows 的文件重命名实际是 `ENTRY_DELETE` + `ENTRY_CREATE`。当前会生成两条事件，但不会关联它们。用户看到的是"删除了旧文件 + 创建了新文件"，而非"重命名了文件"。

### P17: 首次启动注册延迟

如果监听目录包含大量子目录（如整个用户目录），`registerTree()` 会遍历所有子目录并逐个注册。这在启动时可能导致数秒延迟，且在此期间的事件会丢失。

## 9.6 代码质量问题

### P18: LogUtil 直接使用 System.out — 低

虽然引入了 SLF4J 依赖，但 `LogUtil` 类直接使用 `System.out.printf()`。SLF4J 从未被实际使用。

### P19: Config 不支持热加载 — 低

Config 在启动时读取一次 `config.properties`，之后修改配置文件需要重启应用。

### P20: extractExtension 逻辑重复 — 低

`WorkTraceApp.extractExtension()` 和 `FileEventRepository` 中的扩展名提取逻辑重复。应抽取到工具类。

### P21: module-info.java 未导出 ui 包 — 低

`com.worktrace.ui.controller` 和 `com.worktrace.ui.view` 只 `opens` 给 javafx.fxml，未 `exports`。这在当前场景下是正确的(FXML 反射需要 opens)，但如果后续需要从外部访问 UI 组件会有问题。

## 9.7 可维护性问题

### P22: 无单元测试 — 严重

整个项目 0 个测试文件。核心的 `ActivityBlockGenerator`、`AggregationContext`、`CategoryClassifier`、所有 Repository 均无测试覆盖。

### P23: 无异常处理策略 — 中

各模块的异常处理不一致：
- `FileWatcherServiceImpl` 捕获 IOException 并 log
- `FileEventRepository` 抛出 SQLException 由调用方处理
- `WorkTraceApp.start()` 声明 `throws Exception` 但未捕获
- 无全局异常处理器

### P24: 无日志框架配置 — 低

虽然引入了 `slf4j-simple`，但无 `simplelogger.properties` 配置文件。日志格式、级别、输出目标均使用默认值。

---

# 10. 后续路线图

## v0.2 — 数据闭环 (建议周期: 1-2 周)

**目标：** 打通"采集 → 聚合 → 持久化 → 展示"完整链路。

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 实现 TimelineService | P0 | 连接 Repository 和 UI |
| 实现 StatisticsService | P0 | 今日概览数据 |
| 接入真实数据到 MainController | P0 | 移除 loadDemoData() |
| EventAggregator 接入 WorkTraceApp | P0 | 事件 → 聚合 → 持久化 |
| 清理旧 DAO 类 | P1 | 删除 dao/ 目录 |
| 添加单元测试 | P1 | ActivityBlockGenerator + Repository |

## v0.3 — 性能与稳定性 (建议周期: 2-3 周)

**目标：** 解决高频事件场景下的性能问题。

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 事件去抖动 (Debounce) | P0 | 500ms 窗口，同路径合并 |
| 异步写入队列 | P0 | ArrayBlockingQueue + 独立写入线程 |
| 批量写入优化 | P1 | executeBatch + 定时 flush |
| 数据清理策略 | P1 | 保留 30 天，自动归档 |
| 全局异常处理 | P1 | UncaughtExceptionHandler |

## v0.4 — 产品功能 (建议周期: 3-4 周)

**目标：** 提升用户体验，增加实用功能。

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 项目自动识别 | P0 | 基于 .git 目录 / pom.xml / package.json |
| 时间线按日切换 | P0 | 日期选择器 + 历史查看 |
| 项目统计视图 | P1 | 各项目活动时长饼图 |
| 搜索/过滤 | P1 | 按文件名、扩展名、项目过滤 |
| 系统托盘 | P2 | 最小化到托盘，后台运行 |
| 数据导出 | P2 | 导出为 JSON / CSV |

## v1.0 — 正式版 (建议周期: 2-3 个月)

**目标：** 可日常使用的产品。

| 任务 | 优先级 | 说明 |
|------|--------|------|
| AI 日报生成 | P0 | 基于活动块生成每日工作总结 |
| 活动类别学习 | P1 | 基于用户行为自动调整分类规则 |
| 多显示器支持 | P2 | 记录当前活跃窗口 |
| 应用使用统计 | P2 | 记录各应用使用时长 |
| 自动更新 | P2 | 检查新版本并提示更新 |

---

# 11. 代码统计

| 指标 | 数量 |
|------|------|
| Java 文件总数 | 30 |
| FXML 文件 | 1 |
| CSS 文件 | 1 |
| SQL 文件 | 1 |
| 配置文件 (pom.xml, module-info) | 2 |
| **总文件数** | **35** |
| Java 代码总行数 | 3,465 |
| CSS 代码行数 | 305 |
| FXML 代码行数 | 115 |
| SQL 代码行数 | 40 |
| **全部代码总行数** | **3,870** |
| 数据库表数量 | 4 (file_event, activity_block, project_info, schema_version) |
| 实体类数量 | 3 (FileEvent, ActivityBlock, ProjectInfo) |
| Repository 类数量 | 3 (FileEventRepository, ActivityRepository, ProjectRepository) |
| Service 接口数量 | 3 (TimelineService, ProjectService, StatisticsService) |
| Service 实现类数量 | 0 |
| Controller 类数量 | 1 (MainController) |
| 自定义 View 类数量 | 3 (ActivityBlockCell, FileDetailCell, TimelineView) |
| 单元测试数量 | 0 |

**按包统计：**

| 包 | Java 文件 | 总行数 |
|---|---|---|
| app | 1 | 139 |
| collector | 4 | 611 |
| database | 7 | 867 |
| model | 3 | 126 |
| service | 3 | 85 |
| timeline | 3 | 470 |
| ui | 4 | 602 |
| util | 2 | 87 |
| module-info | 1 | 26 |

---

# 12. 最重要的 10 个源码文件

| 排名 | 文件路径 | 行数 | 作用 | 为什么重要 |
|------|---------|------|------|-----------|
| 1 | `timeline/ActivityBlockGenerator.java` | 199 | 聚合引擎核心算法 | 系统核心价值所在。将原始文件事件转化为人类可理解的活动块。算法正确性直接决定产品可用性。 |
| 2 | `timeline/AggregationContext.java` | 248 | 聚合上下文(状态机) | 承载聚合算法的全部状态：文件去重、类别统计、项目识别、合并判断、摘要生成。是最复杂的单个类。 |
| 3 | `collector/FileWatcherServiceImpl.java` | 306 | 文件监听实现 | 数据采集入口。递归注册、噪声过滤、事件分发的完整实现。线程模型设计影响整个系统的数据质量。 |
| 4 | `database/FileEventRepository.java` | 342 | 文件事件持久化 | 批量插入、分页查询、多维度统计的完整实现。是数据流的"蓄水池"，所有查询最终都经过它。 |
| 5 | `ui/controller/MainController.java` | 340 | 主界面控制器 | UI 层的核心。协调侧边栏、时间线、详情面板的联动。当前使用演示数据，接入真实数据后是整个应用的"大脑"。 |
| 6 | `database/DatabaseManager.java` | 80 | 数据库连接管理 | 单例模式，自动建表，WAL 模式。所有数据操作的基础依赖。schema.sql 的解析和执行逻辑在此。 |
| 7 | `collector/CategoryClassifier.java` | 116 | 文件分类器 | 40+ 种扩展名到 5 大类别的映射。分类准确性直接影响聚合质量和用户感知。 |
| 8 | `model/FileEvent.java` | 53 | 文件事件实体 | 全系统数据流转的核心载体。从 WatchService 产生，经过聚合引擎，最终持久化到 SQLite。 |
| 9 | `resources/css/style.css` | 305 | 暗色主题样式 | 定义了 IntelliJ IDEA 风格的完整视觉体系。包含 60+ 个 CSS 类，覆盖所有 UI 组件。是用户体验的基础。 |
| 10 | `resources/sql/schema.sql` | 40 | 数据库 DDL | 定义了 4 张表的结构和索引。是整个数据层的"契约"，任何字段变更都需要同步更新 Entity、Repository、Migration。 |

---

**审计结论：**

WorkTrace 作为 MVP 第一阶段，**架构骨架已搭建完成**，包结构清晰，核心算法（聚合引擎）设计合理。主要问题集中在：

1. **数据流断裂** — 写入和读取未闭合，UI 使用硬编码数据
2. **性能隐患** — WatchService 线程同步写数据库，无去抖动
3. **零测试覆盖** — 核心算法无任何测试保障
4. **Service 层空壳** — 三个 Service 接口无实现类

建议优先完成 v0.2 路线图，实现数据闭环后再进行产品功能扩展。
