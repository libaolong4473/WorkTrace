# WorkTrace

简体中文 | [English](./README.md)

一款桌面工作轨迹记录工具，自动将文件系统事件转化为清晰、可读的工作时间线，帮助用户了解每天创建、修改和处理了哪些文件。

## 它做什么

WorkTrace 在后台静默监听你的文件。当你编辑代码、更新文档或修改配置时，它会将成千上万条原始文件事件聚合成有意义的活动块：

```
09:01 - 09:35  │  代码开发  ·  WorkTraceApp.java +5 个文件
10:05 - 10:20  │  文档编辑  ·  README.md
10:30 - 10:50  │  配置修改  ·  pom.xml +3 个文件
```

你看到的不是 47 次零散的文件保存，而是 3 个工作时段。

## 为什么需要 WorkTrace？

我们每天花大量时间写代码、改文档、调配置——但到了周五，大部分工作已经模糊不清。站会变成了含糊的回忆，周报靠猜，月度述职只能从半记得的 git commit 里拼凑。

WorkTrace 就是为了对抗这种遗忘而生的。

它是你日常工作的**永久数字档案**。不需要手动记录，不需要自律习惯。只需启动监听，正常工作，WorkTrace 会在后台默默构建一条清晰的时间线——记录的是你**实际做了什么**，而不是你以为做了什么。几个月后，你可以调出任意一天的记录，精确看到哪些文件被修改过、每个项目花了多长时间、是什么类型的工作。

把它想象成**整个机器的 git log**，而不仅仅是代码仓库。你的记忆会模糊，WorkTrace 不会。

## 功能特性

- **自动文件监听** — 基于 NIO WatchService 的递归目录监听
- **智能事件聚合** — 将关联文件事件分组为活动块（默认 15 分钟时间窗口）
- **类别自动分类** — 40+ 种文件扩展名映射到 CODE / DOCUMENT / IMAGE / VIDEO / CONFIG
- **项目感知分组** — 同一项目的文件自动合并，不受类型限制
- **SQLite 持久化** — 所有事件和活动块本地存储，WAL 模式
- **暗色主题 UI** — IntelliJ IDEA 风格界面，包含时间线、详情面板和类别统计
- **实时刷新** — 监听运行时每 30 秒自动刷新 UI

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

编辑 `~/.worktrace/config.properties`：

```properties
# 监听目录（分号分隔）
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# 聚合时间窗口（分钟）
aggregate.gap.minutes=15
```

修改后重启应用生效。

### 使用方法

1. 启动应用
2. 点击左侧边栏的 **开始监听**
3. 正常工作——编辑文件、写代码、改文档
4. 点击 **停止监听**，查看聚合后的活动块
5. 点击任意活动块，查看涉及的文件列表

## 项目结构

```
src/main/java/com/worktrace/
├── app/                    # 应用入口
│   └── WorkTraceApp.java
├── collector/              # 文件事件采集层
│   ├── FileWatcherService      # WatchService 接口
│   ├── FileWatcherServiceImpl  # 递归目录监听实现
│   ├── EventAggregator         # 缓冲事件 → ActivityBlock 管道
│   └── CategoryClassifier      # 扩展名 → 类别映射
├── database/               # 持久化层
│   ├── DatabaseManager         # SQLite 连接单例
│   ├── FileEventRepository     # file_event 表 CRUD
│   ├── ActivityRepository      # activity_block 表 CRUD
│   ├── ProjectRepository       # project_info 表 CRUD
│   ├── PageResult              # 分页结果封装
│   └── migration/              # 数据库版本迁移
├── model/                  # 实体类
│   ├── FileEvent
│   ├── ActivityBlock
│   └── ProjectInfo
├── service/                # 业务服务接口
│   ├── TimelineService
│   ├── ProjectService
│   ├── StatisticsService
│   └── impl/
│       └── TimelineServiceImpl
├── timeline/               # 聚合引擎
│   ├── ActivityBlockGenerator  # 核心算法（单遍扫描）
│   ├── AggregationContext      # 滑动窗口状态机
│   └── MergeConfig             # 聚合参数配置
├── ui/                     # JavaFX 表现层
│   ├── controller/MainController
│   └── view/
│       ├── ActivityBlockCell   # 时间线卡片单元格
│       └── FileDetailCell      # 文件列表单元格
└── util/                   # 工具类
    ├── Config
    └── LogUtil
```

## 数据流

```
Windows 文件系统
       │
       ▼
  NIO WatchService（单守护线程）
       │
       ▼
  FileWatcherServiceImpl
       │
       ├──→ FileEventRepository.insert()     → SQLite (file_event)
       │
       └──→ EventAggregator.accept()
               │ 缓冲区（100 条事件）
               ▼
            flush()
               │
               ▼
            ActivityBlockGenerator.generate()
               │
               ▼
            ActivityRepository.batchInsert()  → SQLite (activity_block)
               │
               ▼
            TimelineService.getDailyTimeline()
               │
               ▼
            MainController.loadAllData()      → JavaFX UI
```

## 数据库

三张表，存储在 `~/.worktrace/worktrace.db`：

| 表名             | 用途                              |
| ---------------- | --------------------------------- |
| `file_event`     | 原始文件系统事件（CREATE / MODIFY / DELETE） |
| `activity_block` | 聚合后的工作时段，包含时间范围和类别 |
| `project_info`   | 已注册的项目根目录                |

去重机制：`UNIQUE(start_time, end_time, category)` 约束防止重复插入活动块。

## 路线图

- [x] 基于 WatchService 的递归目录监听
- [x] 事件聚合引擎（时间窗口 + 项目 + 类别合并）
- [x] SQLite 持久化，支持批量插入和分页查询
- [x] 暗色主题 UI，真实数据驱动
- [x] 监听运行时每 30 秒自动刷新
- [ ] 事件去抖动（合并短时间内的重复 MODIFY 事件）
- [ ] 异步写入队列（解耦 WatchService 和数据库写入）
- [ ] 项目自动识别（基于 .git / pom.xml / package.json）
- [ ] 日期选择器，支持查看历史时间线
- [ ] 项目统计视图
- [ ] 系统托盘后台运行
- [ ] AI 驱动的每日工作报告生成

## 开源协议

MIT
