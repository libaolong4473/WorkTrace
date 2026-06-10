package com.worktrace.database.dao;

import com.worktrace.database.DatabaseManager;
import com.worktrace.model.ProjectInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ProjectInfo 数据访问对象。
 * 负责 project_info 表的 CRUD 操作。
 *
 * 职责：
 *   - insert / upsert：新增或更新项目信息
 *   - findAll：列出所有已识别项目
 *   - findByPath：按根路径查找(判断某路径是否属于已知项目)
 */
public class ProjectInfoDao {

    private final Connection conn;

    public ProjectInfoDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public long insert(ProjectInfo project) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO project_info (project_name, root_path)
            VALUES (?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, project.getProjectName());
            ps.setString(2, project.getRootPath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<ProjectInfo> findAll() throws SQLException {
        // TODO: 实现查询全部项目
        return new ArrayList<>();
    }

    public Optional<ProjectInfo> findByPath(String path) throws SQLException {
        // TODO: 实现按路径查找
        return Optional.empty();
    }
}
