# WorkTrace

[简体中文](./README_zh.md) | English

> **Your memory fades. WorkTrace remembers.**

WorkTrace is a personal digital activity timeline that continuously observes your file activity and transforms raw file events into a clear, searchable record of what you actually did — without manual tracking, without discipline, without effort.

Most people cannot accurately answer: **"What did I actually do today?"**

WorkTrace can.

---

## What It Does

WorkTrace runs quietly in the background, observing how files are created, modified, and deleted across your digital workspace. It then aggregates thousands of low-level file events into meaningful **work sessions** — human-readable records of your actual work.

Instead of seeing 47 individual file saves, you see this:

```
09:00 - 10:00  │  WorkTrace Development  │  5 activity blocks · 12 files · 60 min
10:30 - 10:55  │  Document Editing       │  2 activity blocks · 3 files  · 25 min
14:00 - 14:15  │  OtherApp Development   │  1 activity block  · 4 files  · 15 min
```

This is not file search. This is not filesystem inspection. This is **digital memory** — a passive, permanent record of your work life.

## Why WorkTrace?

We spend our days writing, editing, designing, downloading, sharing, and organizing files. Yet by Friday, most of it is a blur. Standups become vague recollections. Weekly reports turn into guesswork. Monthly reviews are reconstructed from half-remembered details.

WorkTrace exists to solve this.

It is a **passive digital archive** of your daily activity. You don't need to log anything. You don't need to change your workflow. You just do your work, and WorkTrace silently builds a timeline of what happened — what you touched, how long you spent, and what kind of activity it was.

Think of it as **git log for your entire machine**, not just code repositories. Months later, you can pull up any date and see exactly what files you worked on, which projects consumed your time, and what kinds of activities filled your day.

## Features

- **Work Session aggregation** — groups activity blocks into meaningful work sessions (30-min window)
- **Automatic activity tracking** — works silently in the background, no manual input needed
- **Smart event aggregation** — groups related file events into activity blocks (15-min window)
- **Category classification** — automatically recognizes code, documents, images, videos, and config files
- **Project recognition** — auto-detects projects from .git, pom.xml, package.json, and 12 more markers
- **Noise filtering** — filters system files, caches, temp files, random filenames, and app-specific noise
- **Event debouncing** — 500ms debounce eliminates 80% of duplicate IDE save events
- **Historical browsing** — date picker + quick buttons (today / yesterday / 7 days / 30 days)
- **Async write queue** — high-performance ArrayBlockingQueue decouples file watching from DB writes
- **Data lifecycle** — automatic 90-day retention for raw events, permanent storage for sessions
- **Persistent history** — all activity stored locally in SQLite with WAL mode
- **Dark theme UI** — IntelliJ IDEA-style interface with resizable three-panel layout
- **File operations** — open files or containing directory from the detail panel

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

Edit `~/.worktrace/config.properties` to customize:

```properties
# Directories to observe (semicolon-separated)
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# Directories to exclude
watch.exclude.dirs=.git;node_modules;target;.idea;LarkCache;WXWork

# File extensions to exclude
watch.exclude.files=.log;.tmp;.wal;.journal;.cache

# Activity grouping window in minutes
aggregate.gap.minutes=5

# Raw event retention days (0 = keep forever)
retention.file_event.days=90
```

### Usage

1. Launch WorkTrace
2. Click **Start Tracking** in the sidebar
3. Go about your normal work — write, edit, download, share
4. Click **Stop Tracking** to see your activity timeline
5. Click any work session to explore activity blocks and files

WorkTrace works best when you forget it's running.

## Project Structure

```
src/main/java/com/worktrace/
├── app/                        # Application entry point
│   └── WorkTraceApp.java
├── collector/                  # Activity collection layer
│   ├── FileWatcherService          # WatchService interface
│   ├── FileWatcherServiceImpl      # Recursive directory monitoring
│   ├── EventAggregator             # Buffered event → activity block pipeline
│   ├── EventWriteQueue             # Async write queue (5000 capacity)
│   ├── EventDebouncer              # 500ms debounce for duplicate events
│   ├── NoiseFilter                 # File-level noise filtering
│   ├── CategoryClassifier          # Extension → category mapping
│   └── ProjectDetector             # Auto project recognition
├── database/                   # Persistence layer
│   ├── DatabaseManager             # SQLite connection singleton
│   ├── FileEventRepository         # file_event table CRUD
│   ├── ActivityRepository          # activity_block table CRUD
│   ├── ProjectRepository           # project_info table CRUD
│   ├── WorkSessionRepository       # work_session table CRUD
│   ├── PageResult                  # Pagination wrapper
│   └── migration/                  # Schema versioning (V1-V4)
├── model/                      # Entity classes
│   ├── FileEvent
│   ├── ActivityBlock
│   ├── ProjectInfo
│   └── WorkSession
├── service/                    # Business logic
│   ├── TimelineService
│   ├── WorkSessionService
│   ├── ProjectService
│   ├── StatisticsService
│   ├── DataRetentionService        # 90-day data lifecycle
│   └── impl/                       # Service implementations
├── timeline/                   # Aggregation engine
│   ├── ActivityBlockGenerator      # FileEvent → ActivityBlock
│   ├── WorkSessionGenerator        # ActivityBlock → WorkSession
│   ├── AggregationContext          # Sliding window state machine
│   └── MergeConfig                 # Aggregation parameters
├── ui/                         # JavaFX presentation layer
│   ├── controller/MainController   # Main UI controller
│   └── view/
│       ├── WorkSessionCell         # WorkSession timeline card
│       ├── ActivityBlockCell       # ActivityBlock detail card
│       ├── FileDetailCell          # File list cell
│       └── ProjectStatsCell        # Project statistics cell
└── util/                       # Utilities
    ├── Config                      # Configuration manager
    └── LogUtil                     # Logging utility
```

## Data Flow

```
Windows File System
       │
       ▼
  NIO WatchService (background thread)
       │
       ├── NoiseFilter 2.0      → filter system noise
       ├── EventDebouncer       → 500ms debounce
       │
       ▼
  EventWriteQueue.submit()       ← non-blocking
       │
       ▼ (Writer Thread, batch 100 or 1s interval)
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
               ▼ (30s refresh)
            WorkSessionService.getDailySessions()
               │
               ▼
            MainController (async Task) → JavaFX UI
```

## Database

Five tables, stored locally at `~/.worktrace/worktrace.db`:

| Table            | Purpose                                          |
| ---------------- | ------------------------------------------------ |
| `file_event`     | Raw file system events (CREATE / MODIFY / DELETE)|
| `activity_block` | Grouped events with time range and category      |
| `project_info`   | Recognized project root paths                    |
| `work_session`   | High-level work sessions (30-min aggregation)    |
| `schema_version` | Migration version tracking                       |

**Lifecycle:** `file_event` records auto-expire after 90 days. `activity_block`, `work_session`, and `project_info` are kept permanently.

## Roadmap

- [x] File monitoring with recursive directory watching
- [x] Smart noise filtering (5-layer: directory, extension, keyword, random name, temp prefix)
- [x] Event debouncing (500ms window)
- [x] Async write queue (decoupled from WatchService thread)
- [x] Activity block aggregation (15-min window)
- [x] Work session aggregation (30-min window)
- [x] Project auto-detection (15 marker files)
- [x] Historical date browsing (date picker + quick buttons)
- [x] Data lifecycle management (90-day retention)
- [x] Dark theme UI with resizable panels
- [x] File open / open containing directory
- [ ] System tray background mode
- [ ] AI-generated daily work reports
- [ ] Productivity insights and time distribution
- [ ] Cross-day pattern discovery
- [ ] Export (Markdown / CSV / JSON)

## License

MIT
