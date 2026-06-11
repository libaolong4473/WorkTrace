# WorkTrace

简体中文 | [English](./README.md)

> **你的记忆会模糊，WorkTrace 不会。**

WorkTrace 是一款个人数字活动时间线工具。它在后台持续观察你的文件活动，将底层的文件事件转化为清晰、可检索的工作记录——不需要手动打卡，不需要刻意习惯，不需要额外操作。

大多数人无法准确回答：**"我今天到底做了什么？"**

WorkTrace 可以。

---

## 它做什么

WorkTrace 在后台安静运行，观察你数字工作空间中文件的创建、修改和删除。它将成千上万条底层文件事件聚合为有意义的活动块——你实际工作的人类可读记录。

你看到的不是 47 次零散的文件保存，而是这样的时间线：

```
09:01 - 09:35  │  软件开发      │  WorkTraceApp.java +5 个文件
10:05 - 10:20  │  文档编辑      │  研究方案.docx
10:30 - 10:50  │  图片处理      │  截图_2026.png +3 个文件
14:10 - 14:30  │  文件下载      │  面试指南.pdf +2 个文件
15:00 - 15:25  │  团队协作      │  共享文档更新
```

这不是文件搜索。这不是文件系统检查。这是**数字记忆**——你工作生活的被动、永久记录。

## 为什么需要 WorkTrace？

我们每天都在写、编辑、设计、下载、分享和整理文件。但到了周五，大部分已经模糊不清。站会变成含糊的回忆，周报靠猜，月度述职只能从半记得的细节里拼凑。

WorkTrace 就是为了改变这一切。

它是你日常活动的**被动数字档案**。你不需要记录任何东西，不需要改变工作方式。你只管正常工作，WorkTrace 会在后台默默构建一条时间线——记录发生了什么、你动了哪些文件、花了多长时间、是什么类型的工作。

把它想象成**整个电脑的 git log**，而不仅仅是代码仓库。几个月后，你可以调出任意一天的记录，精确看到你处理了哪些文件、哪些项目占用了你的时间、一天中发生了哪些类型的活动。

## 活动时间线示例

WorkTrace 眼中的一天：

```
09:01 - 09:35  │  软件开发      │  WorkTraceApp.java +5 个文件
10:05 - 10:20  │  文档编辑      │  研究方案.docx
10:30 - 10:50  │  图片处理      │  截图_2026.png +3 个文件
11:00 - 11:45  │  软件开发      │  MainController.java +8 个文件
14:10 - 14:30  │  文件下载      │  面试指南.pdf +2 个文件
15:00 - 15:25  │  团队协作      │  共享文档更新
15:30 - 16:10  │  配置修改      │  pom.xml +3 个文件
16:20 - 17:00  │  文档编辑      │  会议纪要.md +2 个文件
```

每一个活动块都在讲述一个故事。不需要任何手动输入。

## 愿景

WorkTrace 的长期目标是将原始数字活动转化为**可检索的个人工作历史**。

今天，它捕获文件级别的事件并将其分组为活动块。未来，它将理解项目、识别模式，并揭示你实际如何分配时间的洞察。

终极愿景：**一个记住你在电脑上所做一切的个人知识系统**，让你再也不用猜测"我当时在做什么"。

## 功能特性

- **自动活动追踪** — 在后台安静运行，无需手动输入
- **智能事件聚合** — 将关联文件事件分组为有意义的活动块
- **类别自动识别** — 自动识别代码、文档、图片、视频和配置文件
- **项目感知分组** — 同一项目的文件自动合并，不受类型限制
- **持久化历史** — 所有活动本地存储在 SQLite 中，随时可以回顾
- **暗色主题 UI** — 简洁现代的界面，包含时间线、详情面板和类别分布
- **实时更新** — 追踪运行时时间线自动刷新

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

编辑 `~/.worktrace/config.properties`，选择要追踪的目录：

```properties
# 观察目录（分号分隔）
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# 活动分组时间窗口（分钟）
aggregate.gap.minutes=15
```

### 使用方法

1. 启动 WorkTrace
2. 点击侧边栏的 **开始追踪**
3. 正常工作——写、编辑、下载、分享
4. 点击 **停止追踪**，查看你的活动时间线
5. 点击任意活动块，探索涉及的文件

WorkTrace 在你忘记它存在的时候效果最好。

## 项目结构

```
src/main/java/com/worktrace/
├── app/                    # 应用入口
│   └── WorkTraceApp.java
├── collector/              # 活动采集层
│   ├── FileWatcherService      # 文件观察接口
│   ├── FileWatcherServiceImpl  # 递归目录监听实现
│   ├── EventAggregator         # 缓冲事件 → 活动块管道
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
数字工作空间（文件、文档、项目）
       │
       ▼
  NIO WatchService（后台观察）
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

三张表，本地存储在 `~/.worktrace/worktrace.db`：

| 表名             | 用途                                    |
| ---------------- | --------------------------------------- |
| `file_event`     | 原始活动事件（CREATE / MODIFY / DELETE）|
| `activity_block` | 分组后的工作时段，包含时间范围和类别    |
| `project_info`   | 已识别的项目根目录                      |

去重机制：`UNIQUE(start_time, end_time, category)` 约束防止重复插入活动记录。

## 未来方向

- [ ] **噪声过滤** — 忽略系统文件、缓存和临时产物
- [ ] **项目识别** — 从 .git、pom.xml、package.json 自动检测项目
- [ ] **历史浏览** — 日期选择器，探索任何过去一天的时间线
- [ ] **效率洞察** — 了解时间如何在项目和活动之间分配
- [ ] **跨日模式** — 发现重复的工作节奏和专注时段
- [ ] **系统托盘模式** — 在后台安静运行，持续记录
- [ ] **AI 工作摘要** — 从活动数据自动生成每日和每周报告
- [ ] **导出与分享** — 将时间线导出为 Markdown、CSV 或 JSON

## 开源协议

MIT
