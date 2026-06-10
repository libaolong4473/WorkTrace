# WorkTrace — 个人工作轨迹记录工具

## 产品定位

WorkTrace 不是文件搜索工具，而是**个人工作轨迹记录工具**。
自动记录 Windows 电脑中的文件变化行为，并将大量文件事件聚合成用户可理解的工作时间线。

## 技术栈

| 层次     | 技术选型                |
|----------|-------------------------|
| 语言     | Java 21 (JPMS)          |
| UI       | JavaFX 21 + FXML        |
| 数据库   | SQLite (WAL 模式)       |
| 构建     | Maven                   |
| 架构模式 | MVC + 事件驱动          |

## 项目目录树

```
WorkTrace/
├── pom.xml                                         ← Maven 构建配置
├── README.md
└── src/
    └── main/
        ├── java/
        │   ├── module-info.java                    ← JPMS 模块声明
        │   └── com/worktrace/
        │       ├── app/
        │       │   └── WorkTraceApp.java           ← 应用入口，启动流程编排
        │       ├── collector/
        │       │   ├── FileWatcherService.java      ← 文件监视服务接口
        │       │   ├── EventAggregator.java         ← 事件聚合器
        │       │   └── CategoryClassifier.java      ← 活动类别分类器
        │       ├── database/
        │       │   ├── DatabaseManager.java         ← 数据库连接管理(单例)
        │       │   ├── dao/
        │       │   │   ├── FileEventDao.java        ← 文件事件 DAO
        │       │   │   ├── ActivityBlockDao.java    ← 活动块 DAO
        │       │   │   └── ProjectInfoDao.java      ← 项目信息 DAO
        │       │   └── migration/
        │       │       └── DatabaseMigration.java   ← 数据库版本迁移
        │       ├── model/
        │       │   ├── FileEvent.java               ← 文件事件实体
        │       │   ├── ActivityBlock.java           ← 活动块实体
        │       │   └── ProjectInfo.java             ← 项目信息实体
        │       ├── service/
        │       │   ├── TimelineService.java         ← 时间线服务接口
        │       │   ├── ProjectService.java          ← 项目服务接口
        │       │   └── StatisticsService.java       ← 统计服务接口
        │       ├── timeline/                        ← (预留) 时间线渲染策略
        │       ├── ui/
        │       │   ├── controller/
        │       │   │   └── MainController.java      ← 主界面控制器
        │       │   └── view/
        │       │       └── TimelineView.java        ← 时间线自定义视图
        │       └── util/
        │           ├── Config.java                  ← 配置管理器
        │           └── LogUtil.java                 ← 日志工具
        └── resources/
            ├── fxml/
            │   └── main.fxml                        ← 主界面布局
            ├── css/
            │   └── style.css                        ← 主题样式
            └── sql/
                └── schema.sql                       ← 数据库 DDL
```

## 启动方式

```bash
# 编译
mvn clean compile

# 运行
mvn javafx:run
```

## 第一阶段范围

仅实现基础框架骨架，不包含业务逻辑。

## 后续扩展路线

- 第二阶段：实现 FileWatcherService + EventAggregator 核心采集逻辑
- 第三阶段：实现时间线渲染 + 统计面板
- 第四阶段：接入 AI 日报生成
