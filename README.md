# WorkTrace

[简体中文](./README_zh.md) | English

A desktop activity timeline tool that transforms file system events into clear, human-readable work history, helping users understand what they created, modified, and worked on each day.

## What It Does

WorkTrace silently watches your files in the background. When you edit code, update documents, or change configs, it aggregates thousands of raw file events into meaningful activity blocks:

```
09:01 - 09:35  │  Code Development  ·  WorkTraceApp.java +5 files
10:05 - 10:20  │  Document Editing  ·  README.md
10:30 - 10:50  │  Config Changes    ·  pom.xml +3 files
```

Instead of seeing 47 individual file saves, you see 3 work sessions.

## Why WorkTrace?

We spend hours writing code, drafting docs, and tweaking configs every day — yet by Friday, most of it is a blur. Standups become vague recollections. Weekly reports turn into guesswork. Monthly reviews are reconstructed from half-remembered git commits.

WorkTrace exists to fight that forgetting.

It is a permanent, passive digital archive of your daily work. No manual input. No discipline required. Just start monitoring, do your work, and WorkTrace silently builds a clear timeline of what you actually did — not what you think you did. Months later, you can pull up any date and see exactly which files you touched, how long you worked on each project, and what kind of work it was.

Think of it as **git log for your entire machine**, not just repositories. Your memory fades. WorkTrace doesn't.

## Features

- **Automatic file monitoring** — recursive directory watching via NIO WatchService
- **Smart event aggregation** — groups related file events into activity blocks (15-min default window)
- **Category classification** — 40+ file extensions mapped to CODE / DOCUMENT / IMAGE / VIDEO / CONFIG
- **Project-aware grouping** — same-project files merge together regardless of type
- **SQLite persistence** — all events and activity blocks stored locally with WAL mode
- **Dark theme UI** — IntelliJ IDEA-style interface with timeline, detail panel, and category stats
- **Real-time refresh** — UI auto-updates every 30 seconds while monitoring is active

## Tech Stack

| Layer         | Technology               |
| ------------- | ------------------------ |
| Language      | Java 21 (JPMS modules)   |
| UI            | JavaFX 21 + FXML         |
| Database      | SQLite (WAL mode)        |
| Build         | Maven                    |
| Architecture  | MVC + Event-Driven       |

## Getting Started

### Prerequisites

- JDK 21+
- Maven 3.6+

### Build and Run

```bash
git clone https://github.com/libaolong4473/WorkTrace.git
cd WorkTrace
mvn clean javafx:run
```

> If Maven uses a different JDK, set `JAVA_HOME` first:
> ```bash
> set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
> mvn clean javafx:run
> ```

### Configuration

Edit `~/.worktrace/config.properties`:

```properties
# Directories to watch (semicolon-separated)
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# Aggregation time window in minutes
aggregate.gap.minutes=15
```

Restart the app after changes.

### Usage

1. Launch the app
2. Click **Start Monitoring** in the left sidebar
3. Work normally — edit files, write code, update docs
4. Click **Stop Monitoring** to see aggregated activity blocks
5. Click any block to view the files involved

## Project Structure

```
src/main/java/com/worktrace/
├── app/                    # Application entry point
│   └── WorkTraceApp.java
├── collector/              # File event collection layer
│   ├── FileWatcherService      # WatchService interface
│   ├── FileWatcherServiceImpl  # Recursive directory monitoring
│   ├── EventAggregator         # Buffered event → ActivityBlock pipeline
│   └── CategoryClassifier      # Extension → category mapping
├── database/               # Persistence layer
│   ├── DatabaseManager         # SQLite connection singleton
│   ├── FileEventRepository     # file_event table CRUD
│   ├── ActivityRepository      # activity_block table CRUD
│   ├── ProjectRepository       # project_info table CRUD
│   ├── PageResult              # Pagination wrapper
│   └── migration/              # Schema versioning
├── model/                  # Entity classes
│   ├── FileEvent
│   ├── ActivityBlock
│   └── ProjectInfo
├── service/                # Business logic interfaces
│   ├── TimelineService
│   ├── ProjectService
│   ├── StatisticsService
│   └── impl/
│       └── TimelineServiceImpl
├── timeline/               # Aggregation engine
│   ├── ActivityBlockGenerator  # Core algorithm (single-pass scan)
│   ├── AggregationContext      # Sliding window state machine
│   └── MergeConfig             # Aggregation parameters
├── ui/                     # JavaFX presentation layer
│   ├── controller/MainController
│   └── view/
│       ├── ActivityBlockCell   # Timeline card cell
│       └── FileDetailCell      # File list cell
└── util/                   # Utilities
    ├── Config
    └── LogUtil
```

## Data Flow

```
Windows File System
       │
       ▼
  NIO WatchService (single daemon thread)
       │
       ▼
  FileWatcherServiceImpl
       │
       ├──→ FileEventRepository.insert()     → SQLite (file_event)
       │
       └──→ EventAggregator.accept()
               │ buffer (100 events)
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

## Database

Three tables, stored at `~/.worktrace/worktrace.db`:

| Table            | Purpose                                          |
| ---------------- | ------------------------------------------------ |
| `file_event`     | Raw file system events (CREATE / MODIFY / DELETE)|
| `activity_block` | Aggregated work sessions with time range and category |
| `project_info`   | Registered project root paths                    |

Deduplication: `UNIQUE(start_time, end_time, category)` prevents duplicate activity blocks.

## Roadmap

- [x] File monitoring with recursive directory watching
- [x] Event aggregation engine (time-window + project + category merging)
- [x] SQLite persistence with batch insert and pagination
- [x] Dark theme UI with real-time data
- [x] 30-second auto-refresh during monitoring
- [ ] Event debouncing (merge rapid MODIFY events)
- [ ] Async write queue (decouple WatchService from DB writes)
- [ ] Project auto-detection (.git / pom.xml / package.json)
- [ ] Date picker for historical timeline browsing
- [ ] Project statistics view
- [ ] System tray background mode
- [ ] AI-powered daily work report generation

## License

MIT
