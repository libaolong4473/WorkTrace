package com.worktrace.service;

import com.worktrace.database.DatabaseManager;
import com.worktrace.util.Config;
import com.worktrace.util.LogUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据生命周期管理服务。
 *
 * 职责：
 *   - 按配置策略自动清理过期数据
 *   - 控制数据库增长
 *   - 应用启动时执行一次
 *
 * 清理策略：
 *   file_event      → 保留 N 天（默认 90 天），过期自动删除
 *   activity_block  → 永久保留
 *   work_session    → 永久保留
 *   project_info    → 永久保留
 *
 * 配置：
 *   retention.file_event.days=90  （~/.worktrace/config.properties）
 *
 * SQL 原理：
 *   DELETE FROM file_event
 *   WHERE event_time < datetime('now', '-90 days')
 *
 * 性能：
 *   - 使用 event_time 索引，删除操作高效
 *   - 每次删除后执行 VACUUM（可选），回收磁盘空间
 *   - 启动时执行一次，不影响运行时性能
 */
public class DataRetentionService {

    private final Connection conn;
    private final int fileEventRetentionDays;

    public DataRetentionService() {
        this.conn = DatabaseManager.getInstance().getConnection();
        this.fileEventRetentionDays = Config.getInstance().getInt("retention.file_event.days", 90);
    }

    /**
     * 执行数据清理。应用启动时调用一次。
     */
    public void cleanup() {
        LogUtil.info("数据生命周期管理: 开始清理...");

        int deletedEvents = cleanupFileEvents();

        LogUtil.info("数据生命周期管理: 完成。"
            + " 删除 file_event: " + deletedEvents + " 条"
            + " (保留策略: " + fileEventRetentionDays + " 天)");
    }

    /**
     * 清理过期的 file_event 记录。
     *
     * @return 删除的记录数
     */
    private int cleanupFileEvents() {
        if (fileEventRetentionDays <= 0) {
            LogUtil.info("file_event 保留天数为 0，跳过清理");
            return 0;
        }

        // SQLite 不支持参数化在 datetime 函数内，使用字符串拼接（安全：值来自配置，非用户输入）
        String sql = "DELETE FROM file_event WHERE event_time < datetime('now', '-" + fileEventRetentionDays + " days')";

        try (Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                LogUtil.info("已删除 " + deleted + " 条过期 file_event (>" + fileEventRetentionDays + " 天)");
            }
            return deleted;
        } catch (SQLException e) {
            LogUtil.error("清理 file_event 失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取 file_event 保留天数。
     */
    public int getFileEventRetentionDays() {
        return fileEventRetentionDays;
    }

    /**
     * 手动触发 VACUUM，回收磁盘空间。
     * 注意：VACUUM 会锁定数据库，建议在空闲时执行。
     */
    public void vacuum() {
        try (Statement stmt = conn.createStatement()) {
            LogUtil.info("执行 VACUUM...");
            stmt.execute("VACUUM");
            LogUtil.info("VACUUM 完成");
        } catch (SQLException e) {
            LogUtil.error("VACUUM 失败: " + e.getMessage());
        }
    }
}
