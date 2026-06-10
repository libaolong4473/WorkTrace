package com.worktrace.database.dao;

import com.worktrace.database.DatabaseManager;
import com.worktrace.model.ActivityBlock;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ActivityBlock 数据访问对象。
 * 负责 activity_block 表的 CRUD 操作。
 *
 * 职责：
 *   - insert：写入聚合后的活动块
 *   - findByDate：按日期查询当日活动块(时间线渲染)
 *   - findOverlapping：查询与给定时间段重叠的块(合并判断)
 */
public class ActivityBlockDao {

    private final Connection conn;

    public ActivityBlockDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public long insert(ActivityBlock block) throws SQLException {
        String sql = """
            INSERT INTO activity_block (start_time, end_time, category, summary)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, block.getStartTime().toString());
            ps.setString(2, block.getEndTime().toString());
            ps.setString(3, block.getCategory());
            ps.setString(4, block.getSummary());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<ActivityBlock> findByDate(LocalDate date) throws SQLException {
        // TODO: 实现按日期查询活动块
        return new ArrayList<>();
    }
}
