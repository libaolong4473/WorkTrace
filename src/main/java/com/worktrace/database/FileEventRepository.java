package com.worktrace.database;

import com.worktrace.model.FileEvent;
import com.worktrace.util.LogUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FileEvent 持久化仓库。
 * 负责 file_event 表的全部读写操作。
 *
 * 功能清单：
 *   - 单条插入 / 批量插入
 *   - 分页查询(支持排序)
 *   - 按日期查询(今日 / 最近 N 天 / 指定日期)
 *   - 按项目路径前缀查询
 *   - 按事件类型统计
 */
public class FileEventRepository {

    private final Connection conn;

    public FileEventRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ==================== 写入 ====================

    /**
     * 插入一条文件事件，返回自增 ID。
     */
    public long insert(FileEvent event) throws SQLException {
        String sql = """
            INSERT INTO file_event (event_type, path, file_name, extension, size, event_time)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.getEventType());
            ps.setString(2, event.getPath());
            ps.setString(3, event.getFileName());
            ps.setString(4, event.getExtension());
            ps.setLong(5, event.getSize());
            ps.setString(6, event.getEventTime().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * 批量插入文件事件。使用事务保证原子性，返回插入行数。
     * 适用于高吞吐场景(大量文件事件攒批写入)。
     */
    public int batchInsert(List<FileEvent> events) throws SQLException {
        if (events == null || events.isEmpty()) return 0;
        String sql = """
            INSERT INTO file_event (event_type, path, file_name, extension, size, event_time)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        int count = 0;
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (FileEvent event : events) {
                    ps.setString(1, event.getEventType());
                    ps.setString(2, event.getPath());
                    ps.setString(3, event.getFileName());
                    ps.setString(4, event.getExtension());
                    ps.setLong(5, event.getSize());
                    ps.setString(6, event.getEventTime().toString());
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
        LogUtil.info("批量插入 " + count + " 条文件事件");
        return count;
    }

    // ==================== 分页查询 ====================

    /**
     * 分页查询全部文件事件。
     *
     * @param page     页码(从 1 开始)
     * @param pageSize 每页条数
     * @param asc      是否按时间正序(false 为倒序，最新事件在前)
     */
    public PageResult<FileEvent> findAll(int page, int pageSize, boolean asc) throws SQLException {
        String order = asc ? "ASC" : "DESC";
        String countSql = "SELECT COUNT(*) FROM file_event";
        String dataSql  = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            ORDER BY event_time %s
            LIMIT ? OFFSET ?
            """.formatted(order);

        long total = executeCount(countSql);
        List<FileEvent> data = executeQuery(dataSql, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按日期分页查询文件事件。
     */
    public PageResult<FileEvent> findByDate(LocalDate date, int page, int pageSize) throws SQLException {
        String countSql = """
            SELECT COUNT(*) FROM file_event
            WHERE event_time >= ? AND event_time < ?
            """;
        String dataSql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            ORDER BY event_time DESC
            LIMIT ? OFFSET ?
            """;
        String from = date.atStartOfDay().toString();
        String to   = date.plusDays(1).atStartOfDay().toString();

        long total = executeCount(countSql, from, to);
        List<FileEvent> data = executeQuery(dataSql, from, to, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 查询今日文件事件(分页)。
     */
    public PageResult<FileEvent> findToday(int page, int pageSize) throws SQLException {
        return findByDate(LocalDate.now(), page, pageSize);
    }

    /**
     * 查询最近 N 天的文件事件(分页)。
     */
    public PageResult<FileEvent> findRecentDays(int days, int page, int pageSize) throws SQLException {
        LocalDate to   = LocalDate.now().plusDays(1);
        LocalDate from = LocalDate.now().minusDays(days - 1);
        String countSql = """
            SELECT COUNT(*) FROM file_event
            WHERE event_time >= ? AND event_time < ?
            """;
        String dataSql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            ORDER BY event_time DESC
            LIMIT ? OFFSET ?
            """;
        String fromStr = from.atStartOfDay().toString();
        String toStr   = to.atStartOfDay().toString();

        long total = executeCount(countSql, fromStr, toStr);
        List<FileEvent> data = executeQuery(dataSql, fromStr, toStr, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按项目路径前缀查询文件事件(分页)。
     * 用 path LIKE 'rootPath%' 匹配属于某项目的所有事件。
     */
    public PageResult<FileEvent> findByProject(String rootPath, int page, int pageSize) throws SQLException {
        String pattern = rootPath + "%";
        String countSql = """
            SELECT COUNT(*) FROM file_event
            WHERE path LIKE ?
            """;
        String dataSql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE path LIKE ?
            ORDER BY event_time DESC
            LIMIT ? OFFSET ?
            """;
        long total = executeCount(countSql, pattern);
        List<FileEvent> data = executeQuery(dataSql, pattern, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按项目路径 + 日期范围查询(分页)。
     */
    public PageResult<FileEvent> findByProjectAndDateRange(
            String rootPath, LocalDate from, LocalDate to, int page, int pageSize) throws SQLException {
        String pattern = rootPath + "%";
        String fromStr = from.atStartOfDay().toString();
        String toStr   = to.plusDays(1).atStartOfDay().toString();
        String countSql = """
            SELECT COUNT(*) FROM file_event
            WHERE path LIKE ? AND event_time >= ? AND event_time < ?
            """;
        String dataSql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE path LIKE ? AND event_time >= ? AND event_time < ?
            ORDER BY event_time DESC
            LIMIT ? OFFSET ?
            """;
        long total = executeCount(countSql, pattern, fromStr, toStr);
        List<FileEvent> data = executeQuery(dataSql, pattern, fromStr, toStr, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按时间范围查询文件事件(不分页，返回全部)。
     * 用于 ActivityBlock 详情面板，查询某活动块时间段内涉及的文件。
     */
    public List<FileEvent> findByTimeRange(LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            ORDER BY event_time ASC
            """;
        return executeQuery(sql, from.toString(), to.toString());
    }

    // ==================== 统计 ====================

    /**
     * 按日期统计事件总数。
     */
    public long countByDate(LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_event WHERE event_time >= ? AND event_time < ?";
        return executeCount(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString());
    }

    /**
     * 按日期 + 事件类型统计。
     */
    public long countByDateAndType(LocalDate date, String eventType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_event WHERE event_time >= ? AND event_time < ? AND event_type = ?";
        return executeCount(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString(), eventType);
    }

    /**
     * 按日期 + 扩展名统计事件数。
     */
    public long countByDateAndExtension(LocalDate date, String extension) throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_event WHERE event_time >= ? AND event_time < ? AND extension = ?";
        return executeCount(sql, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString(), extension);
    }

    /**
     * 查询指定日期内各扩展名的事件数量。
     */
    public List<StringCount> countGroupByExtension(LocalDate date) throws SQLException {
        String sql = """
            SELECT extension, COUNT(*) AS cnt
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            GROUP BY extension
            ORDER BY cnt DESC
            """;
        List<StringCount> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StringCount(rs.getString("extension"), rs.getLong("cnt")));
                }
            }
        }
        return result;
    }

    /**
     * 查询指定日期内各事件类型的数量。
     */
    public List<StringCount> countGroupByEventType(LocalDate date) throws SQLException {
        String sql = """
            SELECT event_type, COUNT(*) AS cnt
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            GROUP BY event_type
            ORDER BY cnt DESC
            """;
        List<StringCount> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StringCount(rs.getString("event_type"), rs.getLong("cnt")));
                }
            }
        }
        return result;
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

    private List<FileEvent> executeQuery(String sql, Object... params) throws SQLException {
        List<FileEvent> result = new ArrayList<>();
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

    private FileEvent mapRow(ResultSet rs) throws SQLException {
        FileEvent e = new FileEvent();
        e.setId(rs.getLong("id"));
        e.setEventType(rs.getString("event_type"));
        e.setPath(rs.getString("path"));
        e.setFileName(rs.getString("file_name"));
        e.setExtension(rs.getString("extension"));
        e.setSize(rs.getLong("size"));
        e.setEventTime(LocalDateTime.parse(rs.getString("event_time")));
        return e;
    }

    // ==================== 内部值对象 ====================

    /**
     * 字符串 + 计数 的简单配对，用于 GROUP BY 统计结果。
     */
    public record StringCount(String key, long count) {}
}
