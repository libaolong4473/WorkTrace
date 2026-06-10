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
        // 后续版本在此追加：
        // if (current < 2) { migrateToV2(); }
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
}
