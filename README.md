# WorkTrace

[简体中文](./README_zh.md) | English

> **Your memory fades. WorkTrace remembers.**

WorkTrace is a personal digital activity timeline that continuously observes your file activity and transforms raw file events into a clear, searchable record of what you actually did — without manual tracking, without discipline, without effort.

Most people cannot accurately answer: **"What did I actually do today?"**

WorkTrace can.

---

## What It Does

WorkTrace runs quietly in the background, observing how files are created, modified, and deleted across your digital workspace. It then aggregates thousands of low-level file events into meaningful activity blocks — human-readable records of your actual work sessions.

Instead of seeing 47 individual file saves, you see this:

```
09:01 - 09:35  │  Software Development  │  WorkTraceApp.java +5 files
10:05 - 10:20  │  Document Editing      │  Research Proposal.docx
10:30 - 10:50  │  Image Processing      │  Screenshot_2026.png +3 files
14:10 - 14:30  │  File Downloads        │  Interview_Guide.pdf +2 files
15:00 - 15:25  │  Team Collaboration    │  Shared Documents Updated
```

This is not file search. This is not filesystem inspection. This is **digital memory** — a passive, permanent record of your work life.

## Why WorkTrace?

We spend our days writing, editing, designing, downloading, sharing, and organizing files. Yet by Friday, most of it is a blur. Standups become vague recollections. Weekly reports turn into guesswork. Monthly reviews are reconstructed from half-remembered details.

WorkTrace exists to solve this.

It is a **passive digital archive** of your daily activity. You don't need to log anything. You don't need to change your workflow. You just do your work, and WorkTrace silently builds a timeline of what happened — what you touched, how long you spent, and what kind of activity it was.

Think of it as **git log for your entire machine**, not just code repositories. Months later, you can pull up any date and see exactly what files you worked on, which projects consumed your time, and what kinds of activities filled your day.

## Example Timeline

A realistic day through WorkTrace's eyes:

```
09:01 - 09:35  │  Software Development  │  WorkTraceApp.java +5 files
10:05 - 10:20  │  Document Editing      │  Research Proposal.docx
10:30 - 10:50  │  Image Processing      │  Screenshot_2026.png +3 files
11:00 - 11:45  │  Software Development  │  MainController.java +8 files
14:10 - 14:30  │  File Downloads        │  Interview_Guide.pdf +2 files
15:00 - 15:25  │  Team Collaboration    │  Shared Documents Updated
15:30 - 16:10  │  Configuration         │  pom.xml +3 files
16:20 - 17:00  │  Document Editing      │  Meeting Notes.md +2 files
```

Every block tells a story. No manual input required.

## Vision

WorkTrace's long-term goal is to transform raw digital activity into a **searchable personal work history**.

Today, it captures file-level events and groups them into activity blocks. Tomorrow, it will understand projects, recognize patterns, and surface insights about how you actually spend your time.

The ultimate vision: **a personal knowledge system that remembers everything you did on your computer**, so you never have to wonder "what was I working on?" again.

## Features

- **Automatic activity tracking** — works silently in the background, no manual input needed
- **Smart event aggregation** — groups related file events into meaningful activity blocks
- **Category classification** — automatically recognizes code, documents, images, videos, and configuration files
- **Project-aware grouping** — files from the same project merge together regardless of type
- **Persistent history** — all activity stored locally in SQLite, always available for review
- **Dark theme UI** — clean, modern interface with timeline, detail panel, and category breakdown
- **Real-time updates** — timeline refreshes automatically while tracking is active

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

Edit `~/.worktrace/config.properties` to choose which directories to track:

```properties
# Directories to observe (semicolon-separated)
watch.dirs=C:\Users\you\Desktop;C:\Users\you\Documents;D:\projects

# Activity grouping window in minutes
aggregate.gap.minutes=15
```

### Usage

1. Launch WorkTrace
2. Click **Start Tracking** in the sidebar
3. Go about your normal work — write, edit, download, share
4. Click **Stop Tracking** to see your activity timeline
5. Click any block to explore the files involved

WorkTrace works best when you forget it's running.

## Project Structure

```
src/main/java/com/worktrace/
├── app/                    # Application entry point
│   └── WorkTraceApp.java
├── collector/              # Activity collection layer
│   ├── FileWatcherService      # File observation interface
│   ├── FileWatcherServiceImpl  # Recursive directory monitoring
│   ├── EventAggregator         # Buffered event → activity block pipeline
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
Digital Workspace (files, documents, projects)
       │
       ▼
  NIO WatchService (background observation)
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

Three tables, stored locally at `~/.worktrace/worktrace.db`:

| Table            | Purpose                                              |
| ---------------- | ---------------------------------------------------- |
| `file_event`     | Raw activity events (CREATE / MODIFY / DELETE)       |
| `activity_block` | Grouped work sessions with time range and category   |
| `project_info`   | Recognized project root paths                        |

Deduplication: `UNIQUE(start_time, end_time, category)` prevents duplicate activity records.

## Future Direction

- [ ] **Noise filtering** — ignore system files, caches, and temporary artifacts
- [ ] **Project recognition** — auto-detect projects from .git, pom.xml, package.json
- [ ] **Historical browsing** — date picker to explore any past day's timeline
- [ ] **Productivity insights** — understand how time is distributed across projects and activities
- [ ] **Cross-day patterns** — discover recurring work rhythms and focus periods
- [ ] **System tray mode** — run silently in the background, always recording
- [ ] **AI-generated work summaries** — automatically produce daily and weekly reports from activity data
- [ ] **Export and sharing** — export timelines as markdown, CSV, or JSON

## License

MIT
