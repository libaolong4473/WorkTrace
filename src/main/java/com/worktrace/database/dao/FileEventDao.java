package com.worktrace.database.dao;

import com.worktrace.database.DatabaseManager;
import com.worktrace.model.FileEvent;
import com.worktrace.util.LogUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FileEvent 数据访问对象。
 * 负责 file_event 表的 CRUD 操作。
 *
 * 职责：
 *   - insert：写入单条文件事件
 *   - findByDate：按日期查询当日事件(时间线视图)
 *   - findByTimeRange：按时间范围查询
 *   - countByEventType：按事件类型分组计数
 */
public class FileEventDao {

    private final Connection conn;

    public FileEventDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

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
     * 查询指定日期的全部文件事件。
     */
    public List<FileEvent> findByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            ORDER BY event_time ASC
            """;
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.plusDays(1).atStartOfDay();
        List<FileEvent> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * 查询指定时间范围内的文件事件。
     */
    public List<FileEvent> findByTimeRange(LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = """
            SELECT id, event_type, path, file_name, extension, size, event_time
            FROM file_event
            WHERE event_time >= ? AND event_time < ?
            ORDER BY event_time ASC
            """;
        List<FileEvent> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * 按事件类型分组计数。
     */
    public long countByDateAndType(LocalDate date, String eventType) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM file_event
            WHERE event_time >= ? AND event_time < ? AND event_type = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            ps.setString(3, eventType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * 统计指定日期的总事件数。
     */
    public long countByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM file_event
            WHERE event_time >= ? AND event_time < ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.atStartOfDay().toString());
            ps.setString(2, date.plusDays(1).atStartOfDay().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
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
}
