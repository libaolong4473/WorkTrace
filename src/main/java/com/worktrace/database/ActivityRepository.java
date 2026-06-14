package com.worktrace.database;

import com.worktrace.model.ActivityBlock;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ActivityBlock 持久化仓库。
 * 负责 activity_block 表的全部读写操作。
 *
 * 功能清单：
 *   - 插入活动块
 *   - 批量插入
 *   - 按日期查询(今日 / 最近 N 天)
 *   - 分页查询
 *   - 按时间段重叠查询(用于合并判断)
 *   - 按类别统计
 */
public class ActivityRepository {

    private final Connection conn;

    public ActivityRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ==================== 写入 ====================

    /**
     * 插入一个活动块，返回自增 ID。
     * 使用 INSERT OR IGNORE，若 (start_time, end_time, category) 已存在则跳过。
     */
    public long insert(ActivityBlock block) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO activity_block (start_time, end_time, category, summary, project_name)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, block.getStartTime().toString());
            ps.setString(2, block.getEndTime().toString());
            ps.setString(3, block.getCategory());
            ps.setString(4, block.getSummary());
            ps.setString(5, block.getProjectName() != null ? block.getProjectName() : "");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * 批量插入活动块。使用事务 + INSERT OR IGNORE 保证原子性和去重。
     * 若 (start_time, end_time, category) 已存在则静默跳过，不抛异常。
     */
    public int batchInsert(List<ActivityBlock> blocks) throws SQLException {
        if (blocks == null || blocks.isEmpty()) return 0;
        String sql = """
            INSERT OR IGNORE INTO activity_block (start_time, end_time, category, summary, project_name)
            VALUES (?, ?, ?, ?, ?)
            """;
        int count = 0;
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ActivityBlock block : blocks) {
                    ps.setString(1, block.getStartTime().toString());
                    ps.setString(2, block.getEndTime().toString());
                    ps.setString(3, block.getCategory());
                    ps.setString(4, block.getSummary());
                    ps.setString(5, block.getProjectName() != null ? block.getProjectName() : "");
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                for (int r : results) {
                    if (r >= 0) count += r;
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        return count;
    }

    // ==================== 查询 ====================

    /**
     * 按日期查询活动块(时间线渲染核心方法)。
     */
    public List<ActivityBlock> findByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, category, summary
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            """;
        return executeQuery(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString());
    }

    /**
     * 查询今日活动块。
     */
    public List<ActivityBlock> findToday() throws SQLException {
        return findByDate(LocalDate.now());
    }

    /**
     * 查询最近 N 天的活动块。
     */
    public List<ActivityBlock> findRecentDays(int days) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, category, summary
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            """;
        LocalDate from = LocalDate.now().minusDays(days - 1);
        LocalDate to   = LocalDate.now().plusDays(1);
        return executeQuery(sql, from.atStartOfDay().toString(), to.atStartOfDay().toString());
    }

    /**
     * 查询与给定时间段重叠的活动块(用于合并判断)。
     * 重叠条件：A.start < B.end AND A.end > B.start
     */
    public List<ActivityBlock> findOverlapping(LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, category, summary
            FROM activity_block
            WHERE start_time < ? AND end_time > ?
            ORDER BY start_time ASC
            """;
        return executeQuery(sql, to.toString(), from.toString());
    }

    /**
     * 分页查询活动块。
     */
    public PageResult<ActivityBlock> findAll(int page, int pageSize) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM activity_block";
        String dataSql  = """
            SELECT id, start_time, end_time, category, summary
            FROM activity_block
            ORDER BY start_time DESC
            LIMIT ? OFFSET ?
            """;
        long total = executeCount(countSql);
        List<ActivityBlock> data = executeQuery(dataSql, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按日期分页查询。
     */
    public PageResult<ActivityBlock> findByDatePaged(LocalDate date, int page, int pageSize) throws SQLException {
        String countSql = """
            SELECT COUNT(*) FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            """;
        String dataSql = """
            SELECT id, start_time, end_time, category, summary
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            LIMIT ? OFFSET ?
            """;
        String from = date.atStartOfDay().toString();
        String to   = date.plusDays(1).atStartOfDay().toString();
        long total = executeCount(countSql, from, to);
        List<ActivityBlock> data = executeQuery(dataSql, from, to, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    // ==================== 时间范围查询 ====================

    /**
     * 按时间范围查询活动块(不分页，返回全部)。
     * 用于 WorkSession 详情面板，查询某会话时间段内涉及的活动块。
     */
    public List<ActivityBlock> findByTimeRange(LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, category, summary, project_name
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            """;
        return executeQuery(sql, from.toString(), to.toString());
    }

    // ==================== 统计 ====================

    /**
     * 按日期统计各活动类别的总时长(分钟)。
     */
    public List<CategoryDuration> durationByCategory(LocalDate date) throws SQLException {
        String sql = """
            SELECT category,
                   COALESCE(SUM(
                       MAX(1, ROUND(
                           (julianday(REPLACE(end_time,   'T', ' '))
                          - julianday(REPLACE(start_time, 'T', ' '))) * 1440
                       ))
                   ), 0) AS minutes
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            GROUP BY category
            ORDER BY minutes DESC
            """;
        List<CategoryDuration> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CategoryDuration(rs.getString("category"), rs.getLong("minutes")));
                }
            }
        }
        return result;
    }

    /**
     * 按日期统计活动块总数。
     */
    public long countByDate(LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM activity_block WHERE start_time >= ? AND start_time < ?";
        return executeCount(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString());
    }

    /**
     * 按日期统计所有活动块的总时长(分钟)。
     * 使用 SQLite julianday 函数计算时间差并求和。
     */
    public long totalDurationByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(
                MAX(1, ROUND(
                    (julianday(REPLACE(end_time,   'T', ' '))
                   - julianday(REPLACE(start_time, 'T', ' '))) * 1440
                ))
            ), 0)
            FROM activity_block
            WHERE start_time >= ? AND start_time < ?
            """;
        return executeCount(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString());
    }

    // ==================== 内部工具 ====================

    private long executeCount(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private List<ActivityBlock> executeQuery(String sql, Object... params) throws SQLException {
        List<ActivityBlock> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private ActivityBlock mapRow(ResultSet rs) throws SQLException {
        ActivityBlock b = new ActivityBlock();
        b.setId(rs.getLong("id"));
        b.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
        b.setEndTime(LocalDateTime.parse(rs.getString("end_time")));
        b.setCategory(rs.getString("category"));
        b.setSummary(rs.getString("summary"));
        b.setProjectName(rs.getString("project_name"));
        return b;
    }

    // ==================== 内部值对象 ====================

    /**
     * 活动类别 + 时长(分钟) 配对。
     */
    public record CategoryDuration(String category, long minutes) {}
}
