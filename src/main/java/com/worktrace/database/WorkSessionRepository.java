package com.worktrace.database;

import com.worktrace.model.WorkSession;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkSession 持久化仓库。
 * 负责 work_session 表的全部读写操作。
 */
public class WorkSessionRepository {

    private final Connection conn;

    public WorkSessionRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ==================== 写入 ====================

    public long insert(WorkSession session) throws SQLException {
        String sql = """
            INSERT INTO work_session
                (start_time, end_time, project_name, category, title, block_count, file_count, duration_minutes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, session);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public int batchInsert(List<WorkSession> sessions) throws SQLException {
        if (sessions == null || sessions.isEmpty()) return 0;
        String sql = """
            INSERT INTO work_session
                (start_time, end_time, project_name, category, title, block_count, file_count, duration_minutes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int count = 0;
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (WorkSession session : sessions) {
                    setParams(ps, session);
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

    public void deleteByDate(LocalDate date) throws SQLException {
        String sql = "DELETE FROM work_session WHERE start_time >= ? AND start_time < ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            ps.executeUpdate();
        }
    }

    // ==================== 查询 ====================

    public List<WorkSession> findByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, project_name, category, title,
                   block_count, file_count, duration_minutes
            FROM work_session
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            """;
        return executeQuery(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString());
    }

    /**
     * 按日期范围查询工作会话。
     */
    public List<WorkSession> findByDateRange(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT id, start_time, end_time, project_name, category, title,
                   block_count, file_count, duration_minutes
            FROM work_session
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            """;
        return executeQuery(sql, from.atStartOfDay().toString(), to.plusDays(1).atStartOfDay().toString());
    }

    public PageResult<WorkSession> findByDatePaged(LocalDate date, int page, int pageSize) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM work_session WHERE start_time >= ? AND start_time < ?";
        String dataSql = """
            SELECT id, start_time, end_time, project_name, category, title,
                   block_count, file_count, duration_minutes
            FROM work_session
            WHERE start_time >= ? AND start_time < ?
            ORDER BY start_time ASC
            LIMIT ? OFFSET ?
            """;
        String from = date.atStartOfDay().toString();
        String to   = date.plusDays(1).atStartOfDay().toString();
        long total = executeCount(countSql, from, to);
        List<WorkSession> data = executeQuery(dataSql, from, to, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    // ==================== 内部工具 ====================

    private void setParams(PreparedStatement ps, WorkSession s) throws SQLException {
        ps.setString(1, s.getStartTime().toString());
        ps.setString(2, s.getEndTime().toString());
        ps.setString(3, s.getProjectName() != null ? s.getProjectName() : "");
        ps.setString(4, s.getCategory() != null ? s.getCategory() : "OTHER");
        ps.setString(5, s.getTitle() != null ? s.getTitle() : "");
        ps.setInt(6, s.getBlockCount());
        ps.setInt(7, s.getFileCount());
        ps.setLong(8, s.getDurationMinutes());
    }

    private long executeCount(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private List<WorkSession> executeQuery(String sql, Object... params) throws SQLException {
        List<WorkSession> result = new ArrayList<>();
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

    private WorkSession mapRow(ResultSet rs) throws SQLException {
        WorkSession s = new WorkSession();
        s.setId(rs.getLong("id"));
        s.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
        s.setEndTime(LocalDateTime.parse(rs.getString("end_time")));
        s.setProjectName(rs.getString("project_name"));
        s.setCategory(rs.getString("category"));
        s.setTitle(rs.getString("title"));
        s.setBlockCount(rs.getInt("block_count"));
        s.setFileCount(rs.getInt("file_count"));
        s.setDurationMinutes(rs.getLong("duration_minutes"));
        return s;
    }
}
