package com.worktrace.database.migration;

import com.worktrace.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库迁移管理器。
 * 负责版本控制和 Schema 升级。
 *
 * 职责：
 *   - 检查当前数据库版本
 *   - 按序执行迁移脚本
 *   - 记录已执行的迁移版本
 *
 * 扩展说明：
 *   后续版本升级时，在此添加新的 migration 方法，
 *   通过 version 号控制执行顺序。
 */
public class DatabaseMigration {

    private final Connection conn;

    public DatabaseMigration() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    /**
     * 执行所有待执行的迁移。
     */
    public void migrate() throws SQLException {
        ensureVersionTable();
        int current = currentVersion();
        if (current < 1) {
            migrateToV1();
        }
        if (current < 2) {
            migrateToV2();
        }
        if (current < 3) {
            migrateToV3();
        }
        if (current < 4) {
            migrateToV4();
        }
    }

    private void ensureVersionTable() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
        }
    }

    private int currentVersion() throws SQLException {
        try (var rs = conn.createStatement()
                .executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void migrateToV1() throws SQLException {
        // V1: 创建所有核心表（即使 schema.sql 已执行，CREATE IF NOT EXISTS 保证幂等）
        try (Statement stmt = conn.createStatement()) {
            // file_event
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS file_event (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type  TEXT    NOT NULL,
                    path        TEXT    NOT NULL,
                    file_name   TEXT    NOT NULL,
                    extension   TEXT    DEFAULT '',
                    size        INTEGER DEFAULT 0,
                    event_time  TEXT    NOT NULL
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_event_time ON file_event(event_time)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_event_ext  ON file_event(extension)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_event_type ON file_event(event_type)");

            // activity_block（含 project_name 列）
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity_block (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_time   TEXT    NOT NULL,
                    end_time     TEXT    NOT NULL,
                    category     TEXT    DEFAULT 'OTHER',
                    summary      TEXT    DEFAULT '',
                    project_name TEXT    DEFAULT ''
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_block_time ON activity_block(start_time, end_time)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_activity_block_dedup ON activity_block(start_time, end_time, category)");

            // project_info
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_info (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_name TEXT    NOT NULL,
                    root_path    TEXT    NOT NULL UNIQUE
                )
                """);
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_project_root ON project_info(root_path)");
        }
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version) VALUES(1)")) {
            ps.executeUpdate();
        }
    }

    private void migrateToV2() throws SQLException {
        // V2: activity_block 增加去重索引 (start_time, end_time, category)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_activity_block_dedup
                    ON activity_block(start_time, end_time, category)
                """);
        }
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version) VALUES(2)")) {
            ps.executeUpdate();
        }
    }

    private void migrateToV3() throws SQLException {
        // V3: activity_block 增加 project_name 列
        // 先检查列是否已存在，避免 ALTER TABLE 失败
        if (!columnExists("activity_block", "project_name")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE activity_block ADD COLUMN project_name TEXT DEFAULT ''");
            }
        }
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version) VALUES(3)")) {
            ps.executeUpdate();
        }
    }

    /**
     * 检查表中是否存在指定列。
     */
    private boolean columnExists(String tableName, String columnName) throws SQLException {
        java.sql.ResultSet rs = conn.createStatement()
                .executeQuery("PRAGMA table_info(" + tableName + ")");
        try {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    return true;
                }
            }
        } finally {
            rs.close();
        }
        return false;
    }

    private void migrateToV4() throws SQLException {
        // V4: 新建 work_session 表
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS work_session (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_time      TEXT    NOT NULL,
                    end_time        TEXT    NOT NULL,
                    project_name    TEXT    DEFAULT '',
                    category        TEXT    DEFAULT 'OTHER',
                    title           TEXT    DEFAULT '',
                    block_count     INTEGER DEFAULT 0,
                    file_count      INTEGER DEFAULT 0,
                    duration_minutes INTEGER DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_work_session_time
                    ON work_session(start_time, end_time)
                """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_work_session_project
                    ON work_session(project_name)
                """);
        }
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version) VALUES(4)")) {
            ps.executeUpdate();
        }
    }
}
