# WorkTrace

简体中文 | [English](./README.md)

> **你的记忆会模糊，WorkTrace 不会。**

WorkTrace 是一款个人数字活动时间线工具。它在后台持续观察你的文件活动，将底层的文件事件转化为清晰、可检索的工作记录——不需要手动打卡，不需要刻意习惯，不需要额外操作。

大多数人无法准确回答：**"我今天到底做了什么？"**

WorkTrace 可以。

---

## 它做什么

WorkTrace 在后台安静运行，观察你数字工作空间中文件的创建、修改和删除。它将成千上万条底层文件事件聚合为有意义的**工作会话**——你实际工作的人类可读记录。

你看到的不是 47 次零散的文件保存，而是这样的时间线：

```
09:00 - 10:00  │  WorkTrace 开发      │  5 个活动块 · 12 个文件 · 60 分钟
10:30 - 10:55  │  文档编辑            │  2 个活动块 · 3 个文件  · 25 分钟
14:00 - 14:15  │  OtherApp 开发       │  1 个活动块 · 4 个文件  · 15 分钟
```

这不是文件搜索。这不是文件系统检查。这是**数字记忆**——你工作生活的被动、永久记录。

## 为什么需要 WorkTrace？

我们每天都在写、编辑、设计、下载、分享和整理文件。但到了周五，大部分已经模糊不清。站会变成含糊的回忆，周报靠猜，月度述职只能从半记得的细节里拼凑。

WorkTrace 就是为了改变这一切。

它是你日常活动的**被动数字档案**。你不需要记录任何东西，不需要改变工作方式。你只管正常工作，WorkTrace 会在后台默默构建一条时间线——记录发生了什么、你动了哪些文件、花了多长时间、是什么类型的工作。

把它想象成**整个电脑的 git log**，而不仅仅是代码仓库。几个月后，你可以调出任意一天的记录，精确看到你处理了哪些文件、哪些项目占用了你的时间、一天中发生了哪些类型的活动。

## 功能特性

- **工作会话聚合** — 将活动块聚合为有意义的工作会话（30 分钟窗口）
- **自动活动追踪** — 在后台安静运行，无需手动输入
- **智能事件聚合** — 将关联文件事件分组为活动块（15 分钟窗口）
- **类别自动识别** — 自动识别代码、文档、图片、视频和配置文件
- **项目自动识别** — 从 .git、pom.xml、package.json 等 15 种标志文件自动检测项目
- **噪声过滤** — 过滤系统文件、缓存、临时文件、随机文件名和应用特定噪声
- **事件去抖** — 500ms 去抖窗口，消除 80% 的 IDE 重复保存事件
- **历史日期浏览** — 日期选择器 + 快捷按钮（今天/昨天/7天/30天）
- **异步写入队列** — 高性能 ArrayBlockingQueue 解耦文件监听和数据库写入
- **数据生命周期** — 原始事件自动保留 90 天，工作会话永久保存
- **持久化历史** — 所有活动本地存储在 SQLite 中（WAL 模式）
- **暗色主题 UI** — IntelliJ IDEA 风格界面，三栏可拖拽布局
- **文件操作** — 从详情面板打开文件或打开文件所在目录

## 技术栈

| 层次         | 技术选型                 |
| ------------ | ------------------------ |
| 语言         | Java 21 (JPMS 模块化)    |
| UI           | JavaFX 21 + FXML         |
| 数据库       | SQLite (WAL 模式)        |
| 构建工具     | Maven                    |
| 架构模式     | MVC + 事件驱动           |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+

### 编译与运行

```bash
git clone https://github.com/libaolong4473/WorkTrace.git
cd WorkTrace
mvn clean javafx:run
```

> 如果 Maven 使用的不是 JDK 21，需要先设置 `JAVA_HOME`：
> ```bash
> set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
> mvn clean javafx:run
> ```

### 配置

编辑 `~/.worktrace/config.properties` 自定义配置：

```properties
# 观察目录（分号分隔）
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# 排除目录
watch.exclude.dirs=.git;node_modules;target;.idea;LarkCache;WXWork

# 排除文件扩展名
watch.exclude.files=.log;.tmp;.wal;.journal;.cache

# 活动分组时间窗口（分钟）
aggregate.gap.minutes=5

# 原始事件保留天数（0 = 永久保留）
retention.file_event.days=90
```

### 使用方法

1. 启动 WorkTrace
2. 点击侧边栏的 **开始追踪**
3. 正常工作——写、编辑、下载、分享
4. 点击 **停止追踪**，查看你的活动时间线
5. 点击任意工作会话，探索活动块和文件

WorkTrace 在你忘记它存在的时候效果最好。

## 项目结构

```
src/main/java/com/worktrace/
├── app/                        # 应用入口
│   └── WorkTraceApp.java
├── collector/                  # 活动采集层
│   ├── FileWatcherService          # WatchService 接口
│   ├── FileWatcherServiceImpl      # 递归目录监听实现
│   ├── EventAggregator             # 缓冲事件 → 活动块管道
│   ├── EventWriteQueue             # 异步写入队列（5000 容量）
│   ├── EventDebouncer              # 500ms 去抖
│   ├── NoiseFilter                 # 文件级噪声过滤
│   ├── CategoryClassifier          # 扩展名 → 类别映射
│   └── ProjectDetector             # 项目自动识别
├── database/                   # 持久化层
│   ├── DatabaseManager             # SQLite 连接单例
│   ├── FileEventRepository         # file_event 表 CRUD
│   ├── ActivityRepository          # activity_block 表 CRUD
│   ├── ProjectRepository           # project_info 表 CRUD
│   ├── WorkSessionRepository       # work_session 表 CRUD
│   ├── PageResult                  # 分页结果封装
│   └── migration/                  # 数据库版本迁移（V1-V4）
├── model/                      # 实体类
│   ├── FileEvent
│   ├── ActivityBlock
│   ├── ProjectInfo
│   └── WorkSession
├── service/                    # 业务逻辑
│   ├── TimelineService
│   ├── WorkSessionService
│   ├── ProjectService
│   ├── StatisticsService
│   ├── DataRetentionService        # 90 天数据生命周期
│   └── impl/                       # 服务实现
├── timeline/                   # 聚合引擎
│   ├── ActivityBlockGenerator      # FileEvent → ActivityBlock
│   ├── WorkSessionGenerator        # ActivityBlock → WorkSession
│   ├── AggregationContext          # 滑动窗口状态机
│   └── MergeConfig                 # 聚合参数配置
├── ui/                         # JavaFX 表现层
│   ├── controller/MainController   # 主界面控制器
│   └── view/
│       ├── WorkSessionCell         # 工作会话时间线卡片
│       ├── ActivityBlockCell       # 活动块详情卡片
│       ├── FileDetailCell          # 文件列表单元格
│       └── ProjectStatsCell        # 项目统计单元格
└── util/                       # 工具类
    ├── Config                      # 配置管理器
    └── LogUtil                     # 日志工具
```

## 数据流

```
Windows 文件系统
       │
       ▼
  NIO WatchService（后台线程）
       │
       ├── NoiseFilter 2.0      → 过滤系统噪声
       ├── EventDebouncer       → 500ms 去抖
       │
       ▼
  EventWriteQueue.submit()       ← 非阻塞
       │
       ▼（Writer 线程，每 100 条或每 1 秒刷盘）
       │
       ├── FileEventRepository.batchInsert()  → SQLite (file_event)
       │
       └── EventAggregator.acceptAll()
               │
               ▼
            ActivityBlockGenerator.generate()
               │
               ▼
            ActivityRepository.batchInsert()  → SQLite (activity_block)
               │
               ▼（每 30 秒刷新）
            WorkSessionService.getDailySessions()
               │
               ▼
            MainController（异步 Task）→ JavaFX UI
```

## 数据库

五张表，本地存储在 `~/.worktrace/worktrace.db`：

| 表名             | 用途                                    |
| ---------------- | --------------------------------------- |
| `file_event`     | 原始文件系统事件（CREATE / MODIFY / DELETE）|
| `activity_block` | 分组后的活动块，包含时间范围和类别      |
| `project_info`   | 已识别的项目根目录                      |
| `work_session`   | 高层工作会话（30 分钟聚合）             |
| `schema_version` | 迁移版本追踪                            |

**生命周期：** `file_event` 记录在 90 天后自动过期删除。`activity_block`、`work_session`、`project_info` 永久保留。

## 路线图

- [x] 基于 WatchService 的递归目录监听
- [x] 智能噪声过滤（5 层：目录、扩展名、关键词、随机文件名、临时前缀）
- [x] 事件去抖（500ms 窗口）
- [x] 异步写入队列（与 WatchService 线程解耦）
- [x] 活动块聚合（15 分钟窗口）
- [x] 工作会话聚合（30 分钟窗口）
- [x] 项目自动识别（15 种标志文件）
- [x] 历史日期浏览（日期选择器 + 快捷按钮）
- [x] 数据生命周期管理（90 天保留策略）
- [x] 暗色主题 UI + 三栏可拖拽布局
- [x] 文件打开 / 打开所在目录
- [ ] 系统托盘后台运行
- [ ] AI 驱动的每日工作报告
- [ ] 生产力洞察与时间分配分析
- [ ] 跨日工作模式发现
- [ ] 数据导出（Markdown / CSV / JSON）

## 开源协议

MIT
