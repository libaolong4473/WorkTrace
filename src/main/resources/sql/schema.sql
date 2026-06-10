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
    category    TEXT    DEFAULT 'OTHER',        -- CODE / DOCUMENT / CONFIG / MEDIA / OTHER
    summary     TEXT    DEFAULT ''              -- 人工或 AI 生成的摘要
);

CREATE INDEX IF NOT EXISTS idx_activity_block_time ON activity_block(start_time, end_time);

-- 项目信息表：自动识别或手动配置的项目根目录
CREATE TABLE IF NOT EXISTS project_info (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    project_name TEXT   NOT NULL,               -- 项目名称
    root_path   TEXT    NOT NULL UNIQUE          -- 项目根目录(绝对路径)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_root ON project_info(root_path);
