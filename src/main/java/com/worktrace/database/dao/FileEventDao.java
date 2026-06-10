package com.worktrace.database.dao;

import com.worktrace.database.DatabaseManager;
import com.worktrace.model.FileEvent;

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
 *   - findByExtension：按扩展名聚合统计
 *   - countByEventType：按事件类型分组计数
 */
public class FileEventDao {

    private final Connection conn;

    public FileEventDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

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

    public List<FileEvent> findByDate(LocalDate date) throws SQLException {
        // TODO: 实现按日期查询
        return new ArrayList<>();
    }

    public List<FileEvent> findByTimeRange(LocalDateTime from, LocalDateTime to) throws SQLException {
        // TODO: 实现按时间范围查询
        return new ArrayList<>();
    }
}
