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
        // V1: 初始 Schema 已由 schema.sql 建表完成，此处仅记录版本号
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
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                ALTER TABLE activity_block ADD COLUMN project_name TEXT DEFAULT ''
                """);
        } catch (SQLException e) {
            // 列已存在时忽略（ALTER TABLE 不支持 IF NOT EXISTS）
            if (!e.getMessage().contains("duplicate column")) {
                throw e;
            }
        }
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version) VALUES(3)")) {
            ps.executeUpdate();
        }
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
